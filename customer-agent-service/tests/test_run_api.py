import pytest
from fastapi.testclient import TestClient

from app.main import app, _verified_scopes
from app.main import RunRequest
from app.main import process_run
from app.main import _invoke
from app.main import _callback
from app.main import health
from app.main import run_request
from app.main import os
from app.main import secrets
from app.main import json
from app.main import base64
from app.main import hmac
from app.main import hashlib
from app.main import time


def test_internal_run_requires_service_secret(monkeypatch):
    monkeypatch.setenv("HM_AGENT_SERVICE_SECRET", "test-service-secret")
    response = TestClient(app).post("/internal/runs", json={
        "conversationId": "conversation-1", "runId": "run-1",
        "message": "鞋子多少钱", "delegationToken": "signed"})
    assert response.status_code == 401


def test_internal_run_queues_background_work(monkeypatch):
    monkeypatch.setenv("HM_AGENT_SERVICE_SECRET", "test-service-secret")
    called = []

    async def fake_run(request):
        called.append(request.runId)

    monkeypatch.setattr("app.main.process_run", fake_run)
    response = TestClient(app).post("/internal/runs",
        headers={"X-Agent-Service-Secret": "test-service-secret"},
        json={"conversationId": "conversation-1", "runId": "run-1",
              "message": "鞋子多少钱", "delegationToken": "signed"})
    assert response.status_code == 202
    assert called == ["run-1"]


def test_signed_scope_cannot_be_forged_with_message_text(monkeypatch):
    monkeypatch.setenv("HM_AGENT_TOKEN_SECRET", "local-test-secret-with-adequate-length")
    from app.agent import tool_names_for_request
    from app.main import _verified_scopes
    from app.main import hmac, hashlib, base64, json, time
    header = base64.urlsafe_b64encode(b'{"alg":"HS256"}').rstrip(b"=").decode()
    payload = base64.urlsafe_b64encode(json.dumps({"aud": "hmall-internal", "sub": "1",
        "scope": ["catalog:read"], "exp": time.time() + 60}).encode()).rstrip(b"=").decode()
    signed = f"{header}.{payload}"
    signature = base64.urlsafe_b64encode(hmac.new(b"local-test-secret-with-adequate-length",
        signed.encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
    token = f"{signed}.{signature}"
    assert "order:read" not in _verified_scopes(token)
    with pytest.raises(ValueError):
        _verified_scopes(f"{header}.A{payload[1:]}.{signature}")


def test_source_event_precedes_completed_answer(monkeypatch):
    import asyncio
    import app.main as main

    async def fake_to_thread(function, request):
        return {"answer": "已创建排队工单", "sources": [{"policyId": 5, "version": 2,
                "title": "退货规则", "effectiveFrom": "2026-09-01T00:00:00"}],
                "images": ["http://127.0.0.1:9000/manual/panel.jpg"],
                "ticket": {"ticketId": "ticket-1", "status": "QUEUED"}}

    sent = []

    async def fake_callback(request, sequence, event_type, data):
        sent.append((sequence, event_type, data))

    monkeypatch.setattr(main.asyncio, "to_thread", fake_to_thread)
    monkeypatch.setattr(main, "_callback", fake_callback)
    asyncio.run(main.process_run(RunRequest(conversationId="conv-1", runId="run-1",
                                          message="请转人工", delegationToken="signed")))
    assert [(sequence, event_type) for sequence, event_type, _ in sent] == [
        (1, "sources"), (2, "images"), (3, "completed")]
    assert json.loads(sent[1][2]) == ["http://127.0.0.1:9000/manual/panel.jpg"]


def test_image_event_precedes_completed_answer(monkeypatch):
    import asyncio
    import app.main as main

    async def fake_to_thread(function, request):
        return {"answer": "说明书答案", "sources": [],
                "images": ["http://127.0.0.1:9000/manual/panel.jpg"]}

    sent = []

    async def fake_callback(request, sequence, event_type, data):
        sent.append((sequence, event_type, data))

    monkeypatch.setattr(main.asyncio, "to_thread", fake_to_thread)
    monkeypatch.setattr(main, "_callback", fake_callback)
    asyncio.run(main.process_run(RunRequest(conversationId="conv-1", runId="run-1",
                                           message="怎么安装", delegationToken="signed")))

    assert [(sequence, event_type) for sequence, event_type, _ in sent] == [
        (1, "images"), (2, "completed")]
    assert json.loads(sent[0][2]) == ["http://127.0.0.1:9000/manual/panel.jpg"]


def test_agent_run_never_emits_ticket_event(monkeypatch):
    import asyncio
    import app.main as main

    async def fake_to_thread(function, request):
        return {"answer": "请点击转人工按钮", "sources": [],
                "ticket": {"ticketId": "must-not-be-used", "status": "QUEUED"}}

    sent = []

    async def fake_callback(request, sequence, event_type, data):
        sent.append((sequence, event_type, data))

    monkeypatch.setattr(main.asyncio, "to_thread", fake_to_thread)
    monkeypatch.setattr(main, "_callback", fake_callback)
    asyncio.run(main.process_run(RunRequest(conversationId="conv-1", runId="run-1",
                                           message="请转人工", delegationToken="signed")))
    assert [event_type for _, event_type, _ in sent] == ["completed"]


def test_model_unavailable_emits_recoverable_error(monkeypatch):
    import asyncio
    import app.main as main

    async def fake_to_thread(function, request):
        raise main.ModelUnavailable("尚未配置客服模型")

    sent = []

    async def fake_callback(request, sequence, event_type, data):
        sent.append((sequence, event_type, data))

    monkeypatch.setattr(main.asyncio, "to_thread", fake_to_thread)
    monkeypatch.setattr(main, "_callback", fake_callback)
    asyncio.run(main.process_run(RunRequest(conversationId="conv-1", runId="run-1",
                                           message="查询商品", delegationToken="signed")))
    assert sent == [(1, "error", "客服模型暂时不可用，请稍后重试或转人工")]


def test_signed_token_must_bind_to_requested_conversation_and_run(monkeypatch):
    from app.main import require_request_binding
    monkeypatch.setenv("HM_AGENT_TOKEN_SECRET", "local-test-secret-with-adequate-length")
    claims = {"aud": "hmall-internal", "sub": "1", "scope": ["catalog:read"],
              "conversation_id": "conversation-1", "run_id": "run-1", "exp": time.time() + 60}
    header = base64.urlsafe_b64encode(b'{"alg":"HS256"}').rstrip(b"=").decode()
    payload = base64.urlsafe_b64encode(json.dumps(claims).encode()).rstrip(b"=").decode()
    signed = f"{header}.{payload}"
    signature = base64.urlsafe_b64encode(hmac.new(b"local-test-secret-with-adequate-length",
        signed.encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
    token = f"{signed}.{signature}"
    assert require_request_binding(RunRequest(conversationId="conversation-1", runId="run-1",
                                              message="查询商品", delegationToken=token)) == {"catalog:read"}
    with pytest.raises(ValueError):
        require_request_binding(RunRequest(conversationId="conversation-2", runId="run-1",
                                           message="查询商品", delegationToken=token))


def test_final_answer_requires_policy_evidence_and_persisted_ticket():
    from app.main import finalize_answer
    assert "无法确认" in finalize_answer("退货规则是什么", "支持七天退货", [], [])["answer"]
    policy = {"sources": [{"policyId": 5, "version": 2, "title": "退货规则",
                            "effectiveFrom": "2026-09-01", "excerpt": "凭购买凭证申请退货。"}],
              "needs_handoff": False}
    grounded = finalize_answer("退货规则是什么", "模型编造的退款承诺", [policy], [])
    assert "凭购买凭证申请退货" in grounded["answer"]
    assert "模型编造" not in grounded["answer"]
    indirect = finalize_answer("几天发货？", "明天肯定到", [], [])
    assert "明天肯定到" not in indirect["answer"]
    factual = finalize_answer("有哪些商品", "任意编造", [],
                              [{"kind": "items", "value": {"items": [{"id": 1, "name": "书包", "price": 1999}]}}])
    assert "书包" in factual["answer"] and "任意编造" not in factual["answer"]
    handoff = finalize_answer("请转人工", "已创建工单", [], [])
    assert "点击“转人工”按钮" in handoff["answer"]
    assert "已创建" not in handoff["answer"]


def test_final_answer_merges_catalog_and_manual_knowledge():
    from app.main import finalize_answer
    facts = [{"kind": "items", "value": {"items": [
        {"id": 7, "name": "HAK180 烫金机", "price": 129900}
    ]}}]
    knowledge = [{"available": True, "answer": "开机后先把温度设置到 110℃。",
                  "image_urls": ["http://127.0.0.1:9000/manual/panel.jpg"]}]

    result = finalize_answer("多少钱，温度怎么设置？", "模型声称可以退款", [], facts, knowledge)

    assert result["answer"].index("1299.00 元") < result["answer"].index("根据产品说明书")
    assert "开机后先把温度设置到 110℃" in result["answer"]
    assert "模型声称" not in result["answer"]
    assert result["images"] == ["http://127.0.0.1:9000/manual/panel.jpg"]


def test_rag_failure_preserves_catalog_facts():
    from app.main import finalize_answer
    facts = [{"kind": "items", "value": {"items": [
        {"id": 7, "name": "HAK180 烫金机", "price": 129900}
    ]}}]
    knowledge = [{"available": False, "answer": "", "image_urls": [],
                  "notice": "说明书知识库暂时不可用，请稍后重试或转人工。"}]

    result = finalize_answer("多少钱，温度怎么设置？", "", [], facts, knowledge)

    assert "HAK180 烫金机" in result["answer"]
    assert "说明书知识库暂时不可用" in result["answer"]


def test_catalog_failure_preserves_rag_answer():
    from app.main import finalize_answer
    facts = [{"kind": "items", "value": {"error": "商城查询服务暂时不可用，请稍后重试"}}]
    knowledge = [{"available": True, "answer": "按住设置键调整温度。", "image_urls": []}]

    result = finalize_answer("多少钱，温度怎么设置？", "", [], facts, knowledge)

    assert "商城查询服务暂时不可用" in result["answer"]
    assert "按住设置键调整温度" in result["answer"]


def test_model_operational_claim_is_not_added_to_rag_answer():
    from app.main import finalize_answer
    knowledge = [{"available": True, "answer": "断电后再清洁设备。", "image_urls": []}]

    result = finalize_answer("怎么清洁？", "我已经替你退款并创建工单", [], [], knowledge)

    assert "断电后再清洁设备" in result["answer"]
    assert "退款" not in result["answer"]
    assert "创建工单" not in result["answer"]


@pytest.mark.parametrize("message", ["它怎么安装？", "怎么安装？", "温度怎么设置？", "这机器怎么调温度？"])
def test_vague_manual_question_emits_product_model_clarification(monkeypatch, message):
    import asyncio
    import app.main as main
    from app.knowledge_tools import make_product_clarification_tool

    async def fake_to_thread(function, request):
        clarification_states = []
        make_product_clarification_tool(clarification_states).invoke({})
        return main.finalize_answer(request.message, "请提供型号", [], [], [], clarification_states)

    sent = []

    async def fake_callback(request, sequence, event_type, data):
        sent.append((sequence, event_type, data))

    monkeypatch.setattr(main.asyncio, "to_thread", fake_to_thread)
    monkeypatch.setattr(main, "_callback", fake_callback)
    asyncio.run(main.process_run(RunRequest(conversationId="conv-1", runId="run-1",
                                           message=message, delegationToken="signed")))

    assert sent == [(1, "completed", "请提供具体产品名称和型号，我再查询对应说明书。")]


def test_product_clarification_is_appended_to_existing_catalog_facts():
    from app.main import finalize_answer
    facts = [{"kind": "items", "value": {"items": [
        {"id": 7, "name": "HAK180 烫金机", "price": 129900}
    ]}}]

    result = finalize_answer("列出在售商品，它怎么安装？", "", [], facts, [],
                             [{"needs_product_clarification": True}])

    assert "HAK180 烫金机" in result["answer"]
    assert "请提供具体产品名称和型号" in result["answer"]
