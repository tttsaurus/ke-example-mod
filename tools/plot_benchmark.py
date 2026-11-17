import argparse
from collections import deque
from dataclasses import dataclass
import json
import math
from pathlib import Path
from statistics import fmean, pstdev


@dataclass(frozen=True)
class Capture:
    path: Path
    session: dict
    frames: list[dict]
    end: dict


def normalize_session(session: dict) -> dict:
    if session.get("protocol") != "kirino-frame-benchmark" or session.get("version") not in (1, 2, 3):
        raise ValueError("unsupported benchmark protocol or version")
    if session["version"] == 1:
        session["statistics"] = {"average": "arithmetic", "standardDeviation": "population", "weighting": "per_frame_sample"}
        session["rolling"] = {"windowSeconds": 0.5, "alignment": "trailing", "warmup": "partial"}
        for metric in session["metrics"]:
            if metric["sampling"] == "event":
                metric["rollingAggregation"] = "none"
            elif metric["id"] == "fps":
                metric["rollingAggregation"] = "frame_rate"
            else:
                metric["rollingAggregation"] = "mean"

    identity = session.get("identity")
    if identity is not None and (not isinstance(identity, dict)
                                 or any(not isinstance(identity.get(key), str) or not identity[key].strip() for key in ("id", "name"))):
        raise ValueError("invalid benchmark identity")

    statistics = session["statistics"]
    if (statistics["average"], statistics["standardDeviation"], statistics["weighting"]) != ("arithmetic", "population", "per_frame_sample"):
        raise ValueError("unsupported statistics definition")
    rolling = session["rolling"]
    if not isinstance(rolling["windowSeconds"], (int, float)) or not math.isfinite(rolling["windowSeconds"]) or rolling["windowSeconds"] <= 0:
        raise ValueError("rolling window must be a finite positive number")
    if rolling["alignment"] != "trailing" or rolling["warmup"] != "partial":
        raise ValueError("unsupported rolling window definition")

    metric_ids = set()
    for metric in session["metrics"]:
        if metric["kind"] not in ("gauge", "cpu_duration", "gpu_duration"):
            raise ValueError("unsupported metric kind")
        if metric["kind"] != "gauge" and (metric["unit"] != "ms" or metric["aggregation"] != "sum"):
            raise ValueError("duration metrics must sum milliseconds")
        if metric["id"] in metric_ids:
            raise ValueError("duplicate metric IDs")
        metric_ids.add(metric["id"])
        if metric["sampling"] == "frame":
            if metric["rollingAggregation"] not in ("mean", "frame_rate"):
                raise ValueError("per-frame metrics require a rolling aggregation")
        elif metric["sampling"] != "event" or metric["rollingAggregation"] != "none":
            raise ValueError("event metrics must not use rolling aggregation")
        if metric["rollingAggregation"] == "frame_rate" and (metric["kind"] != "gauge" or metric["unit"] != "fps"):
            raise ValueError("frame_rate aggregation requires an FPS gauge")
    if not metric_ids:
        raise ValueError("session contains no metrics")
    return session


def validate_statistics_groups(groups: list[dict], completed_frames: int) -> None:
    group_ids = set()
    for group in groups:
        if not isinstance(group["id"], str) or not group["id"] or group["id"] in group_ids:
            raise ValueError("invalid or duplicate statistics group ID")
        group_ids.add(group["id"])
        if not isinstance(group["name"], str) or not group["name"].strip():
            raise ValueError("statistics group names must not be empty")
        if group["frameSampling"] != "observed_completed_frames":
            raise ValueError("unsupported statistics frame sampling")
        if not isinstance(group["frames"], int) or not 0 < group["frames"] <= completed_frames:
            raise ValueError("invalid statistics group frame count")
        statistic_ids = set()
        for statistic in group["statistics"]:
            if not isinstance(statistic["id"], str) or not statistic["id"] or statistic["id"] in statistic_ids:
                raise ValueError("invalid or duplicate statistic ID")
            statistic_ids.add(statistic["id"])
            if any(not isinstance(statistic[key], str) or not statistic[key].strip() for key in ("name", "unit")):
                raise ValueError("statistic names and units must not be empty")
            if statistic["source"] not in ("counter", "vertex_shader_invocations") or statistic["aggregation"] != "mean_per_frame":
                raise ValueError("unsupported statistic source or aggregation")
            if not isinstance(statistic["total"], int) or statistic["total"] < 0:
                raise ValueError("statistic totals must be nonnegative integers")
            samples, missing = statistic["frames"], statistic["missingFrames"]
            if not isinstance(samples, int) or not isinstance(missing, int) or samples < 0 or missing < 0 or samples + missing != group["frames"]:
                raise ValueError("invalid statistic frame counts")
            average = statistic["average"]
            if samples == 0:
                if average is not None or statistic["total"] != 0:
                    raise ValueError("statistics without samples must have a null average and zero total")
            elif not isinstance(average, (int, float)) or not math.isfinite(average) or not math.isclose(average, statistic["total"] / samples, rel_tol=1e-12, abs_tol=1e-12):
                raise ValueError("statistic average does not match its total and frame count")
            if "standardDeviation" in statistic:
                deviation = statistic["standardDeviation"]
                if samples == 0:
                    if deviation is not None:
                        raise ValueError("statistics without samples must have a null standard deviation")
                elif not isinstance(deviation, (int, float)) or not math.isfinite(deviation) or deviation < 0:
                    raise ValueError("statistic standard deviations must be finite and nonnegative")


def read_capture(path: Path) -> Capture:
    session = None
    frames = []
    end = None
    with path.open(encoding="utf-8") as source:
        for line_number, line in enumerate(source, 1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
                record_type = record["type"]
                if session is None:
                    if record_type != "session":
                        raise ValueError("the first record must be a session")
                    session = normalize_session(record)
                    metric_ids = [metric["id"] for metric in record["metrics"]]
                elif end is not None:
                    raise ValueError("records found after the end record")
                elif record_type == "frame":
                    if record["frame"] != len(frames):
                        raise ValueError("frame indices must be consecutive and start at zero")
                    if record["frameNs"] <= 0 or record["elapsedNs"] <= 0:
                        raise ValueError("frame timings must be positive")
                    if frames and record["elapsedNs"] <= frames[-1]["elapsedNs"]:
                        raise ValueError("frame timestamps must increase")
                    for metric_id, value in record["values"].items():
                        if metric_id not in metric_ids or not isinstance(value, (int, float)) or not math.isfinite(value):
                            raise ValueError("unknown metric or non-finite sample")
                    for metric_id, count in record.get("missing", {}).items():
                        metric = next((metric for metric in session["metrics"] if metric["id"] == metric_id), None)
                        if metric is None or metric["kind"] != "gpu_duration" or not isinstance(count, int) or count <= 0:
                            raise ValueError("invalid missing GPU sample count")
                        if metric_id in record["values"]:
                            raise ValueError("incomplete GPU samples must be omitted from frame values")
                    frames.append(record)
                elif record_type == "end":
                    if record["frames"] != len(frames):
                        raise ValueError("the end record has the wrong frame count")
                    groups = record["statisticsGroups"] if session["version"] >= 3 else record.get("statisticsGroups", [])
                    if not isinstance(groups, list):
                        raise ValueError("statisticsGroups must be an array")
                    validate_statistics_groups(groups, len(frames))
                    end = record
                else:
                    raise ValueError(f"unexpected record type: {record_type}")
            except (ValueError, KeyError, TypeError) as error:
                raise ValueError(f"{path}:{line_number}: {error}") from error
    if session is None or end is None:
        raise ValueError(f"{path}: incomplete capture; expected session and end records")
    if not frames:
        raise ValueError(f"{path}: capture contains no completed frames")
    return Capture(path, session, frames, end)


def rolling_average(frames: list[dict], metric: dict, window_seconds: float) -> tuple[list[float], list[float]]:
    metric_id = metric["id"]
    frame_rate = metric["rollingAggregation"] == "frame_rate"
    window = deque()
    total = 0.0
    times = []
    values = []
    for frame in frames:
        value = frame["values"].get(metric_id)
        time = frame["elapsedNs"] / 1_000_000_000
        while window and window[0][0] < time - window_seconds:
            total -= window.popleft()[1]
        if value is None:
            continue
        contribution = frame["frameNs"] if frame_rate else value
        window.append((time, contribution))
        total += contribution
        times.append(time)
        values.append(len(window) * 1_000_000_000 / total if frame_rate else total / len(window))
    return times, values


def plot(captures: list[Capture], output: Path, window_seconds: float | None, show: bool, dpi: int = 300) -> None:
    import matplotlib

    if not show:
        matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    metrics = {}
    for capture in captures:
        for metric in capture.session["metrics"]:
            if metric["id"] in metrics and metrics[metric["id"]] != metric:
                raise ValueError(f"incompatible definitions for metric {metric['id']}")
            metrics[metric["id"]] = metric

    plt.rcParams.update({"font.size": 10, "axes.spines.top": False, "axes.spines.right": False})
    groups = [(index, group) for index, capture in enumerate(captures) for group in capture.end.get("statisticsGroups", [])]
    dashboard_heights = [0.45 + 0.4 * (len(group["statistics"]) + 1) for _, group in groups]
    labels = []
    for capture in captures:
        identity = capture.session.get("identity")
        recorded_groups = capture.end.get("statisticsGroups", [])
        if identity:
            labels.append(identity["name"])
        elif len(recorded_groups) == 1:
            labels.append(recorded_groups[0]["name"])
        else:
            labels.append(capture.path.stem)
    labels = [f"{label} (run {index + 1})" if labels.count(label) > 1 else label for index, label in enumerate(labels)]
    figure = plt.figure(layout="constrained")
    figure.set_size_inches(13.0, 2.7 * len(metrics) + sum(dashboard_heights))
    heights = dashboard_heights + [2.7] * len(metrics)
    grid = figure.add_gridspec(len(heights), 1, height_ratios=heights)
    group_names = [group["name"] for _, group in groups]
    for index, (capture_index, group) in enumerate(groups):
        dashboard = figure.add_subplot(grid[index])
        dashboard.set_axis_off()
        group_title = group["name"]
        if group_names.count(group_title) > 1:
            group_title += f" (run {capture_index + 1})"
        dashboard.set_title(group_title, loc="left", fontsize=11, fontweight="bold")
        rows = []
        row_colors = []
        for statistic in group["statistics"]:
            average = statistic["average"]
            deviation = statistic.get("standardDeviation")
            rows.append([statistic["name"], f"{average:,.2f}" if average is not None else "N/A",
                         f"{deviation:,.2f}" if deviation is not None else "N/A", statistic["unit"],
                         f"{statistic['frames']:,} / {group['frames']:,}"])
            row_colors.append(["#f1f5f9" if len(row_colors) % 2 == 0 else "#ffffff"] * 5)
        table = dashboard.table(cellText=rows, colLabels=["Statistic", "Average", "SD", "Unit", "Valid frames"],
                                cellColours=row_colors, colWidths=[0.35, 0.16, 0.16, 0.18, 0.15],
                                cellLoc="left", colLoc="left", bbox=[0, 0, 1, 0.85])
        table.auto_set_font_size(False)
        table.set_fontsize(8)
        for (row, _), cell in table.get_celld().items():
            cell.set_edgecolor("#dbe2ea")
            if row == 0:
                cell.set_facecolor("#dce7f2")
                cell.set_text_props(weight="bold")
    axes = []
    for row in range(len(metrics)):
        axes.append(figure.add_subplot(grid[row + len(groups)], sharex=axes[0] if axes else None))
    title = captures[0].session["name"] if len(captures) == 1 else "Benchmark comparison"
    figure.suptitle(f"{title} (per-frame capture)", fontsize=16)

    for axis, metric in zip(axes, metrics.values()):
        metric_id = metric["id"]
        for capture_index, capture in enumerate(captures):
            samples = [(frame["elapsedNs"] / 1_000_000_000, frame["values"][metric_id])
                       for frame in capture.frames if metric_id in frame["values"]]
            if not samples:
                continue
            times, values = zip(*samples)
            average = fmean(values)
            standard_deviation = pstdev(values)
            summary = f"raw: avg={average:.4g} {metric['unit']}, sd={standard_deviation:.4g} {metric['unit']}"
            missing = sum(frame.get("missing", {}).get(metric_id, 0) for frame in capture.frames)
            sample_summary = f"{len(samples)} frames"
            if missing:
                sample_summary += f", {missing} missing samples"
            color = f"C{capture_index % 10}"
            label = labels[capture_index] if len(captures) > 1 else metric["name"]
            if metric["sampling"] == "event":
                axis.vlines(times, 0, values, color=color, alpha=0.25, linewidth=1)
                axis.scatter(times, values, color=color, s=24, label=f"{label} ({sample_summary})\n{summary}", zorder=3)
            else:
                axis.plot(times, values, color=color, alpha=0.2, linewidth=0.6)
                window = window_seconds if window_seconds is not None else capture.session["rolling"]["windowSeconds"]
                smoothed_times, smoothed_values = rolling_average(capture.frames, metric, window)
                rolling_summary = f"rolling: avg={fmean(smoothed_values):.4g} {metric['unit']}, sd={pstdev(smoothed_values):.4g} {metric['unit']}"
                axis.plot(smoothed_times, smoothed_values, color=color, linewidth=1.5,
                          label=f"{label} ({window:g}s rolling, {sample_summary})\n{summary}\n{rolling_summary}")
        axis.set_title(metric["name"], loc="left", fontsize=11)
        axis.set_ylabel(metric["unit"])
        axis.set_ylim(bottom=0)
        axis.tick_params(labelbottom=axis == axes[-1])
        axis.grid(alpha=0.18)
        if axis.has_data():
            axis.legend(loc="upper right", fontsize=8)
        else:
            message = "No samples recorded\nraw: avg=N/A, sd=N/A"
            if metric["sampling"] == "frame":
                message += "\nrolling: avg=N/A, sd=N/A"
            axis.text(0.5, 0.5, message, ha="center", va="center", transform=axis.transAxes)

    axes[-1].set_xlabel("Elapsed time (s)")
    output.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(output, dpi=dpi)
    if show:
        plt.show()
    plt.close(figure)


def main() -> None:
    parser = argparse.ArgumentParser(description="Plot version 1, 2 and 3 per-frame benchmark JSONL captures with statistics dashboards.")
    parser.add_argument("captures", type=Path, nargs="+")
    parser.add_argument("-o", "--output", type=Path, help="PNG, SVG or PDF output; defaults to the first capture's name with .png")
    parser.add_argument("--window", type=float, help="override the capture's rolling window in seconds (version 1 defaults to 0.5)")
    parser.add_argument("--dpi", type=int, default=300, help="export resolution in dots per inch (default: 300)")
    parser.add_argument("--show", action="store_true", help="also open an interactive plot window")
    arguments = parser.parse_args()
    if arguments.window is not None and (not math.isfinite(arguments.window) or arguments.window <= 0):
        parser.error("--window must be a finite positive number")
    if arguments.dpi <= 0:
        parser.error("--dpi must be a positive integer")
    try:
        captures = [read_capture(path) for path in arguments.captures]
        output = arguments.output or arguments.captures[0].with_suffix(".png")
        if any(output.resolve() == capture.path.resolve() for capture in captures):
            raise ValueError("output must not overwrite an input capture")
        plot(captures, output, arguments.window, arguments.show, arguments.dpi)
    except ImportError:
        parser.exit(1, "Install plotting dependencies: python -m pip install -r tools/requirements-benchmark.txt\n")
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")
    print(output)


if __name__ == "__main__":
    main()
