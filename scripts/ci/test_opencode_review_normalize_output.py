from opencode_review_normalize_output import (
    admits_missing_structural_review,
    mentions_changed_file_evidence,
    current_changed_files,
    mentions_actual_changed_file,
    check_structural_approval,
    valid_control,
    iter_json_objects,
    main,
)
import json
import os
import pytest
from pathlib import Path

def test_admits_missing_structural_review():
    assert admits_missing_structural_review("no changes detected", "Summary") is True
    assert admits_missing_structural_review("Review went well", "All looks good") is False

def test_mentions_changed_file_evidence():
    assert mentions_changed_file_evidence("I checked file.py", "It is good") is True
    assert mentions_changed_file_evidence("I checked nothing", "It is bad") is False

def test_current_changed_files(monkeypatch, tmp_path):
    assert current_changed_files() is None

    file_path = tmp_path / "changed.txt"
    file_path.write_text("file.py\n#comment\n")
    monkeypatch.setenv("OPENCODE_CHANGED_FILES_FILE", str(file_path))
    assert current_changed_files() == ["file.py"]

    monkeypatch.setenv("OPENCODE_CHANGED_FILES_FILE", "/nonexistent")
    assert current_changed_files() is None

def test_mentions_actual_changed_file(monkeypatch, tmp_path):
    assert mentions_actual_changed_file("file.py is bad", "") is True

    file_path = tmp_path / "changed.txt"
    file_path.write_text("file.py\n")
    monkeypatch.setenv("OPENCODE_CHANGED_FILES_FILE", str(file_path))
    assert mentions_actual_changed_file("file.py looks good", "") is True
    assert mentions_actual_changed_file("other.py looks good", "") is False

def test_check_structural_approval(tmp_path):
    control = tmp_path / "control.json"
    control.write_text("invalid json")
    assert check_structural_approval(control) == 65

    control.write_text("[]")
    assert check_structural_approval(control) == 4

    control.write_text('{"result": "APPROVE", "reason": "no changes detected", "summary": "none"}')
    assert check_structural_approval(control) == 4

    control.write_text('{"result": "APPROVE", "reason": "all good", "summary": "none"}')
    assert check_structural_approval(control) == 4 # doesn't mention actual changed file

    control.write_text('{"result": "APPROVE", "reason": "checked file.py", "summary": "good"}')
    assert check_structural_approval(control) == 0

def test_valid_control():
    assert valid_control([], expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    valid_data = {
        "head_sha": "a", "run_id": "1", "run_attempt": "1",
        "result": "APPROVE", "reason": "checked file.py", "summary": "good"
    }
    assert valid_control(valid_data, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is not None
    assert valid_control(valid_data, expected_head_sha="b", expected_run_id="1", expected_run_attempt="1") is None
    assert valid_control(valid_data, expected_head_sha="a", expected_run_id="2", expected_run_attempt="1") is None
    assert valid_control(valid_data, expected_head_sha="a", expected_run_id="1", expected_run_attempt="2") is None

    bad_result = valid_data.copy()
    bad_result["result"] = "UNKNOWN"
    assert valid_control(bad_result, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    empty_reason = valid_data.copy()
    empty_reason["reason"] = "   "
    assert valid_control(empty_reason, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    empty_summary = valid_data.copy()
    empty_summary["summary"] = "   "
    assert valid_control(empty_summary, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    approve_with_findings = valid_data.copy()
    approve_with_findings["findings"] = [{"path": "file.py"}]
    assert valid_control(approve_with_findings, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    reject_no_findings = valid_data.copy()
    reject_no_findings["result"] = "REQUEST_CHANGES"
    assert valid_control(reject_no_findings, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    reject_with_findings = reject_no_findings.copy()
    reject_with_findings["findings"] = [{
        "path": "file.py", "severity": "HIGH", "title": "t", "problem": "p", "root_cause": "r",
        "fix_direction": "f", "regression_test_direction": "r", "suggested_diff": "d", "line": 1
    }]
    assert valid_control(reject_with_findings, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is not None

    bad_finding_line = reject_with_findings.copy()
    bad_finding_line["findings"][0]["line"] = "not an int"
    assert valid_control(bad_finding_line, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    bad_finding_missing_field = reject_with_findings.copy()
    del bad_finding_missing_field["findings"][0]["severity"]
    assert valid_control(bad_finding_missing_field, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

def test_iter_json_objects():
    res = iter_json_objects('some text {"a": 1} more text')
    assert res == [{"a": 1}]

    res = iter_json_objects('{"a": 1}')
    assert res == [{"a": 1}, {"a": 1}]

    res = iter_json_objects('some text')
    assert res == []

def test_main(tmp_path):
    assert main([]) == 64

    control = tmp_path / "control.json"
    control.write_text('{"result": "APPROVE", "reason": "checked file.py", "summary": "good"}')
    assert main(["prog", "--check-structural-approval", str(control)]) == 0

    out = tmp_path / "out.txt"
    assert main(["prog", "a", "1", "1", str(out)]) == 65

    out.write_text('some text')
    assert main(["prog", "a", "1", "1", str(out)]) == 4

    out.write_text('{"head_sha": "a", "run_id": "1", "run_attempt": "1", "result": "APPROVE", "reason": "checked file.py", "summary": "good"}')
    assert main(["prog", "a", "1", "1", str(out)]) == 0
    assert "opencode-review-control-v1" in out.read_text()

def test_valid_control_missing_coverages():
    valid_data = {
        "head_sha": "a", "run_id": "1", "run_attempt": "1",
        "result": "APPROVE", "reason": "no changes detected", "summary": "none"
    }
    assert valid_control(valid_data, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    valid_data["reason"] = "checked no file"
    assert valid_control(valid_data, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    reject = valid_data.copy()
    reject["result"] = "REQUEST_CHANGES"
    reject["findings"] = [{"line": 1}] # miss field
    assert valid_control(reject, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    reject_not_dict = valid_data.copy()
    reject_not_dict["result"] = "REQUEST_CHANGES"
    reject_not_dict["findings"] = ["not a dict"]
    assert valid_control(reject_not_dict, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

def test_iter_json_objects_bad_json():
    # hit except json.JSONDecodeError inside loop
    res = iter_json_objects('{not json')
    assert res == []

def test_main_continue_loop(tmp_path):
    out = tmp_path / "out.txt"
    # first is bad, second is good
    out.write_text('{"result": "bad"} {"head_sha": "a", "run_id": "1", "run_attempt": "1", "result": "APPROVE", "reason": "checked file.py", "summary": "good"}')
    assert main(["prog", "a", "1", "1", str(out)]) == 0

def test_valid_control_missing_coverages2():
    valid_data = {
        "head_sha": "a", "run_id": "1", "run_attempt": "1",
        "result": "APPROVE", "reason": "checked file.py", "summary": "good"
    }
    valid_data["findings"] = [{"path": "a"}] # approve with findings should be none
    assert valid_control(valid_data, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

def test_valid_control_missing_coverages3():
    valid_data = {
        "head_sha": "a", "run_id": "1", "run_attempt": "1",
        "result": "APPROVE", "reason": "checked file.py", "summary": "good"
    }

    # line 190
    reject = valid_data.copy()
    reject["result"] = "REQUEST_CHANGES"
    reject["findings"] = None
    assert valid_control(reject, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None

    reject = valid_data.copy()
    reject["result"] = "REQUEST_CHANGES"
    reject["findings"] = [] # empty array
    assert valid_control(reject, expected_head_sha="a", expected_run_id="1", expected_run_attempt="1") is None
