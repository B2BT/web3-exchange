#!/usr/bin/env python3
"""批量生成各服务的 K8s Deployment+Service 清单（基于模板）"""
import os
import yaml

BASE = "/Users/yongzx/IdeaProjects/web3-exchange/k8s/base"

# 服务 → 端口
SERVICES = {
    "auth": 8102, "user": 8101, "asset": 8103, "market": 8106,
    "chain": 8105, "notify": 8107, "monitor": 8108, "admin": 8109,
    "margin": 8110, "staking": 8112, "risk": 8114, "ticket": 8116,
    "futures": 8117,
}

def gen(service: str, port: int) -> str:
    env = [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "k8s"},
        {"name": "JAVA_OPTS", "value": "-Xms256m -Xmx512m"},
        {"name": "SPRING_CLOUD_NACOS_SERVER_ADDR", "value": "nacos.infra:8848"},
        {"name": "SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR", "value": "nacos.infra:8848"},
        {"name": "SPRING_DATASOURCE_URL",
         "value": f"jdbc:mysql://mysql.infra:3306/web3_exchange?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai"},
        {"name": "SPRING_DATA_REDIS_HOST", "value": "redis.infra"},
        {"name": "ROCKETMQ_NAME_SERVER", "value": "rocketmq-namesrv.infra:9876"},
        {"name": "SPRING_KAFKA_BOOTSTRAP_SERVERS", "value": "kafka.infra:9092"},
    ]
    # market 服务需要 kafka（行情管道）
    if service == "market":
        env.append({"name": "SERVER-SETTINGS_KAFKA_BOOTSTRAP-SERVERS", "value": "kafka.infra:9092"})
    deploy = {
        "apiVersion": "apps/v1",
        "kind": "Deployment",
        "metadata": {"name": f"exchange-{service}", "namespace": "exchange",
                     "labels": {"app": f"exchange-{service}"}},
        "spec": {
            "replicas": 1,
            "selector": {"matchLabels": {"app": f"exchange-{service}"}},
            "template": {
                "metadata": {"labels": {"app": f"exchange-{service}"}},
                "spec": {
                    "containers": [{
                        "name": f"exchange-{service}",
                        "image": f"ghcr.io/b2bt/web3-exchange-{service}:latest",
                        "imagePullPolicy": "IfNotPresent",
                        "ports": [{"containerPort": port}],
                        "env": env,
                        "resources": {
                            "requests": {"memory": "256Mi", "cpu": "250m"},
                            "limits": {"memory": "512Mi", "cpu": "500m"},
                        },
                        "readinessProbe": {
                            "httpGet": {"path": "/actuator/health", "port": port},
                            "initialDelaySeconds": 40,
                            "periodSeconds": 10,
                        },
                    }]
                }
            }
        }
    }
    svc = {
        "apiVersion": "v1",
        "kind": "Service",
        "metadata": {"name": f"exchange-{service}", "namespace": "exchange",
                     "labels": {"app": f"exchange-{service}"}},
        "spec": {
            "selector": {"app": f"exchange-{service}"},
            "ports": [{"name": "http", "port": port, "targetPort": port}],
            "type": "ClusterIP",
        }
    }
    return yaml.safe_dump_all([deploy, svc], sort_keys=False, allow_unicode=True)

def main():
    for svc, port in SERVICES.items():
        path = os.path.join(BASE, f"{svc}.yaml")
        with open(path, "w") as f:
            f.write(gen(svc, port))
        print(f"生成 {svc}.yaml (port={port})")

if __name__ == "__main__":
    main()