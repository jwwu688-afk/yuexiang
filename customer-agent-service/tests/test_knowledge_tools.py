from app.rag_client import RagAnswer, RagUnavailable
from app.knowledge_tools import (make_knowledge_tool, make_product_clarification_tool,
                                 make_catalog_product_resolver)
from concurrent.futures import ThreadPoolExecutor
from threading import Event, Lock
import time


def trust_candidate(value):
    return value


class FakeRagClient:
    def __init__(self, result=None, error=None):
        self.result = result
        self.error = error
        self.calls = []

    def query(self, question, session_id):
        self.calls.append((question, session_id))
        if self.error:
            raise self.error
        return self.result


def test_tool_records_rag_answer_and_images():
    client = FakeRagClient(RagAnswer("先设置到 110℃", ("http://img/panel.jpg",)))
    collected = []
    tool = make_knowledge_tool(client, "run-42", collected, trust_candidate)

    result = tool.invoke({"product_name": "HAK180 烫金机", "topic": "参数设置"})

    assert result == {
        "available": True,
        "answer": "先设置到 110℃",
        "image_urls": ["http://img/panel.jpg"],
    }
    assert collected == [result]
    assert client.calls == [("HAK180 烫金机 参数设置", "run-42")]


def test_tool_maps_rag_unavailable_to_recoverable_state():
    client = FakeRagClient(error=RagUnavailable("说明书知识库暂时不可用"))
    collected = []

    result = make_knowledge_tool(client, "run-42", collected, trust_candidate).invoke({
        "product_name": "HAK180 烫金机", "topic": "安装"})

    assert result == {
        "available": False,
        "answer": "",
        "image_urls": [],
        "notice": "说明书知识库暂时不可用，请稍后重试或转人工。",
    }
    assert collected == [result]


def test_second_tool_call_reuses_first_result():
    client = FakeRagClient(RagAnswer("第一次答案", ()))
    collected = []
    tool = make_knowledge_tool(client, "run-42", collected, trust_candidate)

    first = tool.invoke({"product_name": "HAK180 烫金机", "topic": "使用方法"})
    second = tool.invoke({"product_name": "HAK180 烫金机", "topic": "故障排查"})

    assert second == first
    assert client.calls == [("HAK180 烫金机 使用方法", "run-42")]
    assert collected == [first]


def test_concurrent_tool_calls_share_one_rag_request():
    entered = Event()
    release = Event()
    calls = []
    calls_lock = Lock()

    class BlockingClient:
        def query(self, question, session_id):
            with calls_lock:
                calls.append((question, session_id))
            entered.set()
            release.wait(timeout=2)
            return RagAnswer("并发答案", ())

    tool = make_knowledge_tool(BlockingClient(), "run-42", [], trust_candidate)
    with ThreadPoolExecutor(max_workers=2) as pool:
        first = pool.submit(tool.invoke, {"product_name": "HAK180 烫金机", "topic": "参数设置"})
        assert entered.wait(timeout=1)
        second = pool.submit(tool.invoke, {"product_name": "HAK180 烫金机", "topic": "安装"})
        time.sleep(0.05)
        release.set()
        assert first.result(timeout=2) == second.result(timeout=2)

    assert calls == [("HAK180 烫金机 参数设置", "run-42")]


def test_order_or_identity_data_is_rejected_before_rag_request():
    client = FakeRagClient(RagAnswer("不应调用", ()))
    collected = []
    tool = make_knowledge_tool(client, "run-42", collected, lambda _: None)

    result = tool.invoke({"product_name": "HAK180 订单 AB123456", "topic": "安装"})

    assert result["available"] is False
    assert "只接受产品型号" in result["notice"]
    assert client.calls == []
    assert collected == [result]


def test_local_rejection_does_not_consume_the_only_rag_attempt():
    client = FakeRagClient(RagAnswer("安装答案", ()))
    collected = []
    tool = make_knowledge_tool(client, "run-42", collected,
                               lambda value: None if "单号" in value else value)

    rejected = tool.invoke({"product_name": "HAK180 单号 AB123456", "topic": "安装"})
    accepted = tool.invoke({"product_name": "HAK180 烫金机", "topic": "安装"})

    assert rejected["available"] is False
    assert accepted["available"] is True
    assert client.calls == [("HAK180 烫金机 安装", "run-42")]
    assert collected == [accepted]


def test_structured_topic_does_not_forward_raw_order_or_cancellation_text():
    client = FakeRagClient(RagAnswer("设置答案", ()))
    tool = make_knowledge_tool(client, "run-42", [], trust_candidate)

    result = tool.invoke({"product_name": "123456 烫金机", "topic": "参数设置"})

    assert result["available"] is True
    assert client.calls == [("123456 烫金机 参数设置", "run-42")]


def test_product_clarification_tool_records_structured_state():
    collected = []

    result = make_product_clarification_tool(collected).invoke({})

    assert result == {"needs_product_clarification": True}
    assert collected == [result]


def test_catalog_resolver_sends_only_java_canonical_product_name_to_rag():
    class Item:
        name = "HAK180 烫金机"

    class CatalogClient:
        def search_items(self, filters):
            assert filters.key == "HAK180 AB123456"
            return [Item()]

    rag = FakeRagClient(RagAnswer("安装答案", ()))
    resolver = make_catalog_product_resolver(CatalogClient())
    tool = make_knowledge_tool(rag, "run-42", [], resolver)

    result = tool.invoke({"product_name": "HAK180 AB123456", "topic": "安装"})

    assert result["available"] is True
    assert rag.calls == [("HAK180 烫金机 安装", "run-42")]
