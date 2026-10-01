from langchain_core.tools import tool

from app.rag_client import RagClient, RagUnavailable


def make_knowledge_tool(client: RagClient, session_id: str,
                        collector: list[dict] | None = None):
    cached: dict | None = None

    @tool("search_product_knowledge")
    def search_product_knowledge(query: str) -> dict:
        """查询明确产品的说明书、安装使用方法、规格含义、维护和故障排查知识。"""
        nonlocal cached
        if cached is not None:
            return cached
        try:
            answer = client.query(query, session_id)
            cached = {
                "available": True,
                "answer": answer.answer,
                "image_urls": list(answer.image_urls),
            }
        except RagUnavailable:
            cached = {
                "available": False,
                "answer": "",
                "image_urls": [],
                "notice": "说明书知识库暂时不可用，请稍后重试或转人工。",
            }
        if collector is not None:
            collector.append(cached)
        return cached

    return search_product_knowledge
