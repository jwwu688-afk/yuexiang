from dataclasses import dataclass
from urllib.parse import urlsplit

import httpx


@dataclass(frozen=True)
class RagAnswer:
    answer: str
    image_urls: tuple[str, ...]


class RagUnavailable(RuntimeError):
    """说明书知识库当前不可用。"""


class RagClient:
    def __init__(self, base_url: str, timeout_seconds: float,
                 allowed_image_origins: tuple[str, ...],
                 client: httpx.Client | None = None):
        self.base_url = base_url.rstrip("/")
        self.client = client or httpx.Client(timeout=httpx.Timeout(timeout_seconds))
        self.allowed_image_origins = {
            self._origin(value) for value in allowed_image_origins if self._origin(value)
        }

    @staticmethod
    def _origin(value: str) -> str:
        parsed = urlsplit((value or "").strip())
        if parsed.scheme.lower() not in ("http", "https") or not parsed.netloc:
            return ""
        return f"{parsed.scheme.lower()}://{parsed.netloc.lower()}"

    def query(self, question: str, session_id: str) -> RagAnswer:
        try:
            response = self.client.post(f"{self.base_url}/query", json={
                "query": question,
                "session_id": session_id,
                "is_stream": False,
            })
            response.raise_for_status()
            payload = response.json()
            answer = payload.get("answer") if isinstance(payload, dict) else None
            if not isinstance(answer, str) or not answer.strip():
                raise ValueError("RAG response is missing answer")
        except (httpx.HTTPError, ValueError, TypeError, AttributeError) as exc:
            raise RagUnavailable("说明书知识库暂时不可用") from exc
        return RagAnswer(answer=answer, image_urls=self._read_images(session_id))

    def _read_images(self, session_id: str) -> tuple[str, ...]:
        try:
            response = self.client.get(
                f"{self.base_url}/history/{session_id}", params={"limit": 5})
            response.raise_for_status()
            items = response.json().get("items") or []
        except (httpx.HTTPError, ValueError, TypeError, AttributeError):
            return ()

        latest = next((item for item in reversed(items)
                       if isinstance(item, dict) and item.get("role") == "assistant"), None)
        if not latest:
            return ()
        images = []
        for value in latest.get("image_urls") or []:
            if not isinstance(value, str) or self._origin(value) not in self.allowed_image_origins:
                continue
            if value not in images:
                images.append(value)
            if len(images) == 5:
                break
        return tuple(images)
