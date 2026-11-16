"""Verify that the retained Python service covers the removed alternative service.

Run from any working directory. No model calls or cloud credentials are required.
"""
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import AsyncMock, patch

PYTHON_ROOT = Path(__file__).resolve().parents[2] / "python"
sys.path.insert(0, str(PYTHON_ROOT))
# Construct clients with a dummy key; no request is sent by these tests.
os.environ["ECOM_LLM_API_KEY"] = "local-test-placeholder"

from fastapi.testclient import TestClient
from models.schemas import AgentResult, Product, RecommendationResponse
from services.ab_test import ABTestEngine
import main


class PythonServiceCoverage(unittest.TestCase):
    def setUp(self):
        self.graph_patch = patch.object(main, "build_recommendation_graph", return_value=object())
        self.graph_patch.start()
        self.client = TestClient(main.app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.graph_patch.stop()

    def test_health_endpoint(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "healthy")

    def test_recommend_endpoint_preserves_request_context_and_response(self):
        response = RecommendationResponse(
            request_id="python-service-test", user_id="u001",
            products=[Product(product_id="P001", name="示例商品", category="耳机", price=199, stock=20)],
            marketing_copies=[{"product_id": "P001", "copy": "测试文案"}],
            agent_results={key: AgentResult(agent_name=key, success=True) for key in
                           ("user_profile", "product_rec", "marketing_copy", "inventory")},
        )
        recommend = AsyncMock(return_value=response)
        with patch.object(main.supervisor, "recommend", recommend):
            result = self.client.post("/api/v1/recommend", json={
                "user_id": "u001", "num_items": 1,
                "context": {"recent_views": ["耳机"], "avg_order_amount": 200},
            })
        self.assertEqual(result.status_code, 200)
        self.assertEqual(result.json()["products"][0]["product_id"], "P001")
        self.assertEqual(len(result.json()["agent_results"]), 4)
        self.assertEqual(recommend.await_args.args[0].context["recent_views"], ["耳机"])

    def test_experiment_endpoint_and_consistent_assignment(self):
        result = self.client.get("/api/v1/experiments")
        self.assertEqual(result.status_code, 200)
        self.assertIn("rec_strategy", result.json())
        engine = ABTestEngine()
        self.assertEqual(engine.assign("u001")["group"], engine.assign("u001")["group"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
