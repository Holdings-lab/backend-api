from __future__ import annotations

from datetime import datetime
from pathlib import Path
import os
import subprocess


def run_prediction_now() -> dict:
    base_dir = Path(__file__).resolve().parent
    script_path = base_dir / "train_regression.py"

    if not script_path.exists():
        return {
            "status": "failed",
            "message": f"예측 스크립트를 찾을 수 없습니다: {script_path}",
            "executed_at": datetime.utcnow().isoformat() + "Z",
        }

    env = dict(**os.environ)
    env["MPLBACKEND"] = "Agg"

    completed = subprocess.run(
        ["python", str(script_path)],
        cwd=str(base_dir),
        env=env,
        capture_output=True,
        text=True,
    )

    return {
        "status": "success" if completed.returncode == 0 else "failed",
        "return_code": completed.returncode,
        "message": "prediction completed" if completed.returncode == 0 else "prediction failed",
        "stdout_tail": (completed.stdout or "")[-3000:],
        "stderr_tail": (completed.stderr or "")[-3000:],
        "executed_at": datetime.utcnow().isoformat() + "Z",
    }
