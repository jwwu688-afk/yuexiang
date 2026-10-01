from app.rag_client import RagAnswer, RagUnavailable
from app.knowledge_tools import make_knowledge_tool


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
    tool = make_knowledge_tool(client, "run-42", collected)

    result = tool.invoke({"query": "HAK180 烫金机温度怎么设置？"})

    assert result == {
        "available": True,
        "answer": "先设置到 110℃",
        "image_urls": ["http://img/panel.jpg"],
    }
    assert collected == [result]
    assert client.calls == [("HAK180 烫金机温度怎么设置？", "run-42")]


def test_tool_maps_rag_unavailable_to_recoverable_state():
    client = FakeRagClient(error=RagUnavailable("说明书知识库暂时不可用"))
    collected = []

    result = make_knowledge_tool(client, "run-42", collected).invoke({"query": "完整问题"})

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
    tool = make_knowledge_tool(client, "run-42", collected)

    first = tool.invoke({"query": "第一个完整问题"})
    second = tool.invoke({"query": "第二个问题"})

    assert second == first
    assert client.calls == [("第一个完整问题", "run-42")]
    assert collected == [first]
