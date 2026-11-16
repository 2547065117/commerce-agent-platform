# 统一使用 Python Agent 服务

本项目原来的 Go 与 Python 是两套独立的同功能实现，不是必须同时运行的微服务。当前统一使用已有 Python 版本，移除重复的 Go 源码与 go.mod，无需安装 Go。

## 功能对应

| 原模块职责 | 当前 Python 实现 |
|---|---|
| Web 服务入口和路由 | `python/main.py`，FastAPI |
| Agent 基类、重试和降级 | `python/agents/base_agent.py` |
| 用户画像 | `python/agents/user_profile_agent.py` |
| 商品召回和排序 | `python/agents/product_rec_agent.py` |
| 营销文案 | `python/agents/marketing_copy_agent.py` |
| 库存判断 | `python/agents/inventory_agent.py` |
| 并行编排和结果聚合 | `python/orchestrator/supervisor.py`，`asyncio.gather()` |
| 请求、响应和商品数据结构 | `python/models/schemas.py`，Pydantic |
| A/B 实验 | `python/services/ab_test.py` |

## 当前运行结构

Vue 页面 → Java / Spring Boot（8080）→ Python / FastAPI（8000）→ 百炼模型。

`enterprise/compose.yml` 和 `enterprise/start-all.ps1` 已使用 Python 服务；本次无需改变现有模型配置或端口。旧的 Go 服务默认端口是 8080，已由 Java 控制台使用，不再启动第二个推荐服务。

保留功能为推荐、用户画像、营销文案、库存判断和实验查询；没有因语言替换新增聊天能力。Python 实验分组策略以其现有实现为准，不承诺与已移除版本逐字段完全一致。

本机启动：在 `enterprise` 执行 `powershell -File start-all.ps1`，访问 `http://127.0.0.1:8080`。

接口验证：在项目根目录执行 `.\.venv\Scripts\python.exe enterprise\tests\test_python_service.py`。测试用替身代替模型，不消耗百炼额度。
