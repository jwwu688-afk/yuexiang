import json

import httpx
import pytest

from app.rag_client import RagClient, RagUnavailable


def make_client(handler, allowed=("http://127.0.0.1:9000",)):
    return RagClient(
        "http://127.0.0.1:8011/",
        timeout_seconds=2.5,
        allowed_image_origins=allowed,
        client=httpx.Client(transport=httpx.MockTransport(handler)),
    )


def test_query_uses_run_id_and_non_stream_mode():
    seen = {}

    def handler(request):
        if request.url.path == "/query":
            seen["payload"] = json.loads(request.content)
            return httpx.Response(200, json={"answer": "先设置到 110℃"})
        assert request.url.path == "/history/run-42"
        assert request.url.params["limit"] == "5"
        return httpx.Response(200, json={"items": []})

    result = make_client(handler).query("HAK180 烫金机温度怎么设置？", "run-42")

    assert seen["payload"] == {
        "query": "HAK180 烫金机温度怎么设置？",
        "session_id": "run-42",
        "is_stream": False,
    }
    assert result.answer == "先设置到 110℃"
    assert result.image_urls == ()


def test_latest_assistant_images_are_filtered_deduplicated_and_bounded():
    allowed = [f"http://127.0.0.1:9000/manual/{number}.jpg" for number in range(1, 8)]

    def handler(request):
        if request.url.path == "/query":
            return httpx.Response(200, json={"answer": "操作步骤"})
        return httpx.Response(200, json={"items": [
            {"role": "assistant", "text": "旧回答", "image_urls": [allowed[0]]},
            {"role": "user", "text": "新问题", "image_urls": []},
            {"role": "assistant", "text": "新回答", "image_urls": [
                allowed[1], allowed[1], *allowed[2:],
                "https://example.com/not-allowed.jpg", "javascript:alert(1)",
            ]},
        ]})

    result = make_client(handler).query("完整问题", "run-42")

    assert result.image_urls == tuple(allowed[1:6])


def test_history_failure_keeps_successful_text_answer():
    def handler(request):
        if request.url.path == "/query":
            return httpx.Response(200, json={"answer": "文字仍然可用"})
        return httpx.Response(503, json={"detail": "history unavailable"})

    result = make_client(handler).query("完整问题", "run-42")

    assert result.answer == "文字仍然可用"
    assert result.image_urls == ()


def test_timeout_raises_rag_unavailable():
    def handler(request):
        raise httpx.ReadTimeout("timeout", request=request)

    with pytest.raises(RagUnavailable, match="说明书知识库暂时不可用"):
        make_client(handler).query("完整问题", "run-42")


def test_non_success_query_response_raises_rag_unavailable():
    client = make_client(lambda _: httpx.Response(503, json={"detail": "unavailable"}))

    with pytest.raises(RagUnavailable, match="说明书知识库暂时不可用"):
        client.query("完整问题", "run-42")


@pytest.mark.parametrize("response", [
    httpx.Response(200, json={"answer": "  "}),
    httpx.Response(200, json={"message": "missing answer"}),
    httpx.Response(200, content=b"not-json"),
])
def test_blank_or_malformed_answer_raises_rag_unavailable(response):
    client = make_client(lambda _: response)

    with pytest.raises(RagUnavailable, match="说明书知识库暂时不可用"):
        client.query("完整问题", "run-42")
