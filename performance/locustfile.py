import os
from urllib.parse import urlparse

from locust import HttpUser, LoadTestShape, between, task


TARGET_HOST = os.getenv("TARGET_HOST", "http://127.0.0.1:8080").rstrip("/")
ALLOW_NON_LOCAL = os.getenv("ALLOW_NON_LOCAL_TARGET", "false").lower() == "true"
LOAD_TEST_TOKEN = os.getenv("LOAD_TEST_TOKEN", "")

target = urlparse(TARGET_HOST)
if target.hostname not in {"127.0.0.1", "localhost", "::1"} and not ALLOW_NON_LOCAL:
    raise RuntimeError(
        "Non-local TARGET_HOST requires ALLOW_NON_LOCAL_TARGET=true"
    )


class BackendUser(HttpUser):
    host = TARGET_HOST
    wait_time = between(0.5, 2.0)

    def on_start(self):
        self.application_id = None
        if LOAD_TEST_TOKEN:
            self.client.headers.update(
                {"Authorization": f"Bearer {LOAD_TEST_TOKEN}"}
            )

    @task(4)
    def public_registry(self):
        self.client.get(
            "/api/public/approved-applications?page=1&size=20",
            name="/api/public/approved-applications",
        )

    @task(2)
    def readiness(self):
        self.client.get(
            "/actuator/health/readiness",
            name="/actuator/health/readiness",
        )

    @task(4)
    def application_list(self):
        if LOAD_TEST_TOKEN:
            self.client.get(
                "/api/applications?page=1&size=20",
                name="/api/applications",
            )

    @task(2)
    def create_and_get_draft(self):
        if not LOAD_TEST_TOKEN:
            return
        response = self.client.post(
            "/api/applications",
            name="/api/applications [create]",
            json={
                "iinOrBin": "000000000000",
                "region": "LOAD_TEST",
                "productionType": "GRAIN",
                "landArea": 10,
                "requestedAmount": 100000,
                "activityType": "CROP_PRODUCTION",
                "applicantCategory": "OTHER",
            },
        )
        if response.status_code == 201:
            self.application_id = response.json().get("id")
        if self.application_id:
            self.client.get(
                f"/api/applications/{self.application_id}",
                name="/api/applications/{id}",
            )

    @task(2)
    def commission_list_and_filter(self):
        if LOAD_TEST_TOKEN:
            self.client.get(
                "/api/commission/applications?page=1&size=20&status=SUBMITTED",
                name="/api/commission/applications",
            )

    @task(2)
    def analytics(self):
        if LOAD_TEST_TOKEN:
            self.client.get(
                "/api/analytics/summary",
                name="/api/analytics/summary",
            )


class BackendLoadShape(LoadTestShape):
    profiles = {
        "smoke": (5, 1),
        "normal": (50, 5),
        "target": (100, 10),
    }

    def tick(self):
        profile = os.getenv("LOAD_PROFILE", "smoke").lower()
        users, spawn_rate = self.profiles.get(profile, self.profiles["smoke"])
        duration = int(os.getenv("LOAD_DURATION_SECONDS", "60"))
        if self.get_run_time() >= duration:
            return None
        return users, spawn_rate
