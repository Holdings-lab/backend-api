from __future__ import annotations

import logging
import os
import time
from contextlib import contextmanager
from typing import Iterator

import requests

logger = logging.getLogger(__name__)


def _env_flag(name: str, default: bool = False) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "y", "on"}


def _ollama_base_url() -> str:
    return (os.getenv("OLLAMA_BASE_URL") or "http://ollama:11434").rstrip("/")


def _ollama_model() -> str:
    return (os.getenv("OLLAMA_MODEL") or "llama3.2:3b").strip() or "llama3.2:3b"


def _container_name() -> str:
    return (os.getenv("OLLAMA_CONTAINER_NAME") or "ollama").strip() or "ollama"


def _ready_timeout_sec() -> int:
    try:
        return max(30, int(os.getenv("OLLAMA_READY_TIMEOUT_SEC", "180")))
    except ValueError:
        return 180


def _mem_limit() -> str:
    return (os.getenv("OLLAMA_MEMORY_LIMIT") or "2g").strip() or "2g"


def is_ollama_ready(timeout_sec: float = 3.0) -> bool:
    try:
        response = requests.get(f"{_ollama_base_url()}/api/tags", timeout=timeout_sec)
        return response.status_code == 200
    except Exception:
        return False


def _docker_client():
    try:
        import docker
    except ImportError as error:
        raise RuntimeError(
            "docker package is not installed; cannot auto-manage Ollama container"
        ) from error

    sock = os.getenv("DOCKER_HOST", "unix:///var/run/docker.sock")
    return docker.DockerClient(base_url=sock)


def _resolve_network_name(client) -> str | None:
    configured = (os.getenv("OLLAMA_DOCKER_NETWORK") or "").strip()
    if configured:
        return configured

    # data-ml 자신과 같은 브리지 네트워크에 ollama 를 붙인다.
    hostname = (os.getenv("HOSTNAME") or "").strip()
    if not hostname:
        return None
    try:
        me = client.containers.get(hostname)
        networks = (me.attrs.get("NetworkSettings") or {}).get("Networks") or {}
        if networks:
            return next(iter(networks.keys()))
    except Exception as error:
        logger.warning("[ollama] failed to detect docker network: %s", error)
    return None


def _ensure_container(client) -> None:
    name = _container_name()
    try:
        container = client.containers.get(name)
        if container.status != "running":
            logger.info("[ollama] starting existing container: %s", name)
            container.start()
        return
    except Exception:
        pass

    network = _resolve_network_name(client)
    if not network:
        raise RuntimeError(
            "Ollama container not found and OLLAMA_DOCKER_NETWORK could not be resolved. "
            "Create it once with: docker compose --profile ollama up -d ollama"
        )

    image = (os.getenv("OLLAMA_IMAGE") or "ollama/ollama:latest").strip()
    volume_name = (os.getenv("OLLAMA_VOLUME_NAME") or "backend-api_ollama-data").strip()
    logger.info(
        "[ollama] creating container name=%s image=%s network=%s mem=%s",
        name,
        image,
        network,
        _mem_limit(),
    )
    client.images.pull(image)
    client.containers.run(
        image=image,
        name=name,
        detach=True,
        restart_policy={"Name": "no"},
        network=network,
        mem_limit=_mem_limit(),
        volumes={volume_name: {"bind": "/root/.ollama", "mode": "rw"}},
        environment={
            "OLLAMA_KEEP_ALIVE": os.getenv("OLLAMA_KEEP_ALIVE", "5m"),
        },
    )


def _wait_until_ready() -> None:
    deadline = time.time() + _ready_timeout_sec()
    while time.time() < deadline:
        if is_ollama_ready(timeout_sec=2.0):
            logger.info("[ollama] ready at %s", _ollama_base_url())
            return
        time.sleep(2)
    raise RuntimeError(
        f"Ollama did not become ready within {_ready_timeout_sec()}s ({_ollama_base_url()})"
    )


def _model_present(names: list[str], model: str) -> bool:
    model = model.strip()
    for name in names:
        name = str(name or "").strip()
        if not name:
            continue
        if name == model:
            return True
        # tags.json 은 보통 "llama3.2:3b" 형태
        if name.startswith(f"{model}:") or model.startswith(f"{name}:"):
            return True
    return False


def _ensure_model() -> None:
    model = _ollama_model()
    try:
        tags = requests.get(f"{_ollama_base_url()}/api/tags", timeout=10).json()
        names = [str(item.get("name") or "") for item in (tags.get("models") or [])]
        if _model_present(names, model):
            logger.info("[ollama] model already present: %s", model)
            return
    except Exception as error:
        logger.warning("[ollama] failed to list models: %s", error)

    logger.info("[ollama] pulling model: %s", model)
    response = requests.post(
        f"{_ollama_base_url()}/api/pull",
        json={"name": model, "stream": False},
        timeout=int(os.getenv("OLLAMA_PULL_TIMEOUT_SEC", "900")),
    )
    if response.status_code >= 400:
        raise RuntimeError(f"Ollama model pull failed: {response.status_code} {response.text[:300]}")
    logger.info("[ollama] model pull finished: %s", model)


def _stop_container() -> None:
    name = _container_name()
    try:
        client = _docker_client()
        container = client.containers.get(name)
        if container.status == "running":
            logger.info("[ollama] stopping container: %s", name)
            container.stop(timeout=20)
    except Exception as error:
        logger.warning("[ollama] stop failed: %s", error)


def ensure_ollama_for_summarization() -> bool:
    """
    요약에 필요할 때만 Ollama 를 기동한다.
    Returns:
        True 이면 이 호출이 컨테이너를 새로 기동했으므로 종료 시 stop 대상.
    """
    if not _env_flag("OLLAMA_AUTO_MANAGE", True):
        logger.info("[ollama] auto-manage disabled")
        return False

    if is_ollama_ready():
        logger.info("[ollama] already running")
        _ensure_model()
        return False

    logger.info("[ollama] not ready; attempting auto-start")
    try:
        client = _docker_client()
        _ensure_container(client)
        _wait_until_ready()
        _ensure_model()
        return True
    except Exception as error:
        logger.warning(
            "[ollama] auto-start failed (%s); summarization will fall back to truncate",
            error,
        )
        return False


def release_ollama(started_by_us: bool) -> None:
    if not started_by_us:
        return
    if not _env_flag("OLLAMA_AUTO_STOP", True):
        logger.info("[ollama] auto-stop disabled; leaving container running")
        return
    _stop_container()


@contextmanager
def ollama_session() -> Iterator[None]:
    """크롤 후처리(요약) 구간에만 Ollama 를 켰다 끈다."""
    started = False
    try:
        started = ensure_ollama_for_summarization()
        yield
    finally:
        release_ollama(started)
