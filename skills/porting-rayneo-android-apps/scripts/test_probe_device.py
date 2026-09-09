"""Offline tests for the probe's device boundary; never launches adb."""

import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("probe_device.py")


class ProbeTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(SCRIPT.is_file(), "The read-only probe has not been implemented")
        spec = importlib.util.spec_from_file_location("probe_device", SCRIPT)
        self.probe = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.probe)

    @staticmethod
    def response(argv, **kwargs):
        command = argv[3:]
        output = "device\n" if command == ["get-state"] else "ok\n"
        if command == ["shell", "am", "get-current-user"]:
            output = "12\n"
        return subprocess.CompletedProcess(argv, 0, output, "")

    def options(self, *extra):
        return self.probe.parse_args(["--serial", "SERIAL", "--timeout", "7", *extra])

    def test_requires_serial_and_rejects_remote_shell_metacharacters(self):
        bad_args = [[], ["--serial", "SERIAL;id"], ["--serial", "$(id)"],
                    ["--serial", "SERIAL\nwhoami"], ["--serial", "--all"],
                    ["--serial", "SERIAL", "--package", "com.example.app;id"],
                    ["--serial", "SERIAL", "--package", "com.example.$APP"],
                    ["--serial", "SERIAL", "--timeout", "nan"]]
        with patch.object(self.probe.subprocess, "run") as run:
            for args in bad_args:
                with self.subTest(args=args), contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit) as raised:
                        self.probe.parse_args(args)
                    self.assertEqual(raised.exception.code, 2)
            run.assert_not_called()
        for serial in ["SERIAL", "HOST:5555", "[::1]:5555", "adb-SERIAL-token._adb-tls-connect._tcp"]:
            self.assertEqual(self.probe.parse_args(["--serial", serial]).serial, serial)

    def test_every_query_is_bound_read_only_and_uses_current_android_user(self):
        with patch.object(self.probe.subprocess, "run", side_effect=self.response) as run:
            report = self.probe.collect_report(self.options("--package", "com.example.reader"))
        self.assertTrue(report["complete"])
        allowed_shell_prefixes = {
            ("getprop",), ("wm", "size"), ("wm", "density"),
            ("dumpsys", "display"), ("dumpsys", "input"),
            ("am", "get-current-user"), ("cmd", "package", "resolve-activity"),
            ("cmd", "package", "list", "packages"),
            ("dumpsys", "package"), ("cmd", "appops", "get"),
        }
        commands = []
        for call in run.call_args_list:
            argv = call.args[0]
            commands.append(argv[3:])
            self.assertIsInstance(argv, list)
            self.assertEqual(argv[:3], ["adb", "-s", "SERIAL"])
            self.assertFalse(call.kwargs["shell"])
            self.assertEqual(call.kwargs["timeout"], 7)
            self.assertTrue(call.kwargs["capture_output"])
            if argv[3:] != ["get-state"]:
                self.assertEqual(argv[3], "shell")
                remote = tuple(argv[4:])
                self.assertTrue(any(remote[:len(prefix)] == prefix for prefix in allowed_shell_prefixes), remote)
        self.assertIn(["shell", "cmd", "appops", "get", "--user", "12",
                       "com.example.reader", "MANAGE_EXTERNAL_STORAGE"], commands)
        for command in commands:
            if "resolve-activity" in command:
                self.assertEqual(command[command.index("--user") + 1], "12")

    def test_no_package_means_no_package_dump_or_appop_query(self):
        with patch.object(self.probe.subprocess, "run", side_effect=self.response) as run:
            self.probe.collect_report(self.options())
        for call in run.call_args_list:
            self.assertNotEqual(call.args[0][3:6], ["shell", "dumpsys", "package"])
            self.assertNotIn("appops", call.args[0])

    def test_package_presence_matches_exact_name_in_current_user(self):
        for output, expected in [("package:com.example.reader.beta\n", False),
                                 ("package:com.example.reader\n", True)]:
            def respond(argv, **kwargs):
                if argv[3:8] == ["shell", "cmd", "package", "list", "packages"]:
                    self.assertEqual(argv[-3:], ["--user", "12", "com.example.reader"])
                    return subprocess.CompletedProcess(argv, 0, output, "")
                return self.response(argv, **kwargs)
            with self.subTest(expected=expected), patch.object(self.probe.subprocess, "run", side_effect=respond):
                report = self.probe.collect_report(self.options("--package", "com.example.reader"))
            self.assertIs(report.get("package_installed_for_user"), expected)

    def test_unavailable_target_stops_before_any_shell_query(self):
        failure = subprocess.CompletedProcess([], 1, "", "device unauthorized")
        with patch.object(self.probe.subprocess, "run", return_value=failure) as run:
            report = self.probe.collect_report(self.options())
        self.assertFalse(report["complete"])
        self.assertEqual(run.call_count, 1)
        self.assertEqual(run.call_args.args[0][3:], ["get-state"])

    def test_timeout_is_explicit_and_other_read_only_queries_continue(self):
        def respond(argv, **kwargs):
            if argv[3:] == ["shell", "wm", "size"]:
                raise subprocess.TimeoutExpired(argv, kwargs["timeout"])
            return self.response(argv, **kwargs)
        with patch.object(self.probe.subprocess, "run", side_effect=respond):
            report = self.probe.collect_report(self.options())
        self.assertFalse(report["complete"])
        self.assertEqual(report["queries"]["wm_size"]["status"], "timeout")
        self.assertEqual(report["queries"]["wm_density"]["status"], "ok")

    def test_invalid_current_user_never_falls_back_to_user_zero(self):
        def respond(argv, **kwargs):
            if argv[3:] == ["shell", "am", "get-current-user"]:
                return subprocess.CompletedProcess(argv, 0, "Unexpected service response", "")
            return self.response(argv, **kwargs)
        with patch.object(self.probe.subprocess, "run", side_effect=respond) as run:
            report = self.probe.collect_report(self.options("--package", "com.example.reader"))
        self.assertFalse(report["complete"])
        self.assertEqual(report["queries"]["current_user"]["status"], "invalid_output")
        self.assertTrue(all("--user" not in call.args[0] for call in run.call_args_list))
        self.assertEqual(report["queries"]["all_files_appop"]["status"], "skipped")

    def test_dump_summaries_drop_unrelated_content_and_bound_size(self):
        def respond(argv, **kwargs):
            if argv[3:6] == ["shell", "dumpsys", "package"]:
                return subprocess.CompletedProcess(argv, 0, "versionName=1.2\n"
                    "    android.permission.MANAGE_EXTERNAL_STORAGE\n"
                    "unrelated_private_field=DO_NOT_RETAIN\n", "")
            if argv[3:] == ["shell", "dumpsys", "input"]:
                return subprocess.CompletedProcess(argv, 0, "Name: cyttsp5_mt\n"
                    "RecentQueue: DO_NOT_RETAIN\n" + "    Sources: 0x1002\n" * 1000, "")
            return self.response(argv, **kwargs)
        with patch.object(self.probe.subprocess, "run", side_effect=respond):
            report = self.probe.collect_report(self.options("--package", "com.example.reader"))
        package = report["queries"]["package_summary"]["stdout"]
        inputs = report["queries"]["input_summary"]["stdout"]
        self.assertIn("versionName=1.2", package)
        self.assertNotIn("DO_NOT_RETAIN", package + inputs)
        self.assertLessEqual(len(inputs), 8192)
        self.assertTrue(report["queries"]["input_summary"]["truncated"])


if __name__ == "__main__":
    unittest.main()
