import re
from threading import Lock
from typing import Literal

from langchain_core.tools import tool

from app.contracts import ItemFilters
from app.java_client import JavaUnavailable
from app.rag_client import RagClient, RagUnavailable


KnowledgeTopic = Literal["安装", "使用方法", "参数设置", "规格含义", "维护保养", "故障排查"]

_PROTECTED_PRODUCT_VALUE = re.compile(
    r"订单|单号|物流|快递|收货|地址|手机号|电话|联系人|用户(?:名|ID)?|账号|"
    r"支付|退款|token|jwt|delegation|\border\b|\btracking\b|\baddress\b|\bphone\b",
    re.IGNORECASE,
)
_PRODUCT_NAME = re.compile(r"[A-Za-z0-9\u4e00-\u9fff][A-Za-z0-9\u4e00-\u9fff._+/#()（）· -]{0,79}")


def _is_safe_product_name(product_name: str) -> bool:
    value = product_name.strip()
    return bool(_PRODUCT_NAME.fullmatch(value)) and not _PROTECTED_PRODUCT_VALUE.search(value)


def make_product_clarification_tool(collector: list[dict] | None = None):
    @tool("request_product_model")
    def request_product_model() -> dict:
        """说明书问题缺少可确认的产品名称或型号时，记录需要向用户澄清。"""
        state = {"needs_product_clarification": True}
        if collector is not None and not collector:
            collector.append(state)
        return state

    return request_product_model


def make_catalog_product_resolver(catalog_client):
    def resolve(candidate: str) -> str | None:
        try:
            rows = catalog_client.search_items(ItemFilters(key=candidate, page_size=5))
        except (JavaUnavailable, PermissionError, ValueError, TypeError):
            return None
        if not rows:
            return None
        normalized = "".join(candidate.casefold().split())
        exact = [row.name for row in rows
                 if "".join(row.name.casefold().split()) == normalized]
        if exact:
            return exact[0]
        return rows[0].name if len(rows) == 1 else None

    return resolve


def make_knowledge_tool(client: RagClient, session_id: str,
                        collector: list[dict] | None,
                        product_resolver):
    cached: dict | None = None
    cache_lock = Lock()
    local_rejections: list[dict] = []

    @tool("search_product_knowledge")
    def search_product_knowledge(product_name: str, topic: KnowledgeTopic) -> dict:
        """按明确产品名和固定知识主题查询说明书；参数不得包含订单、物流或个人数据。"""
        nonlocal cached
        product_name = product_name.strip()
        with cache_lock:
            if cached is not None:
                return cached
            canonical_name = (product_resolver(product_name)
                              if _is_safe_product_name(product_name) else None)
            if not canonical_name:
                rejected = {
                    "available": False,
                    "answer": "",
                    "image_urls": [],
                    "notice": "产品知识查询只接受产品型号和说明书问题，请移除订单、物流或个人信息后重试。",
                }
                if collector is not None:
                    collector.append(rejected)
                    local_rejections.append(rejected)
                return rejected
            try:
                answer = client.query(f"{canonical_name} {topic}", session_id)
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
                for rejected in local_rejections:
                    if rejected in collector:
                        collector.remove(rejected)
                local_rejections.clear()
                collector.append(cached)
            return cached

    return search_product_knowledge
