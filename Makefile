.PHONY: help build up down restart logs test clean backup shell status publish

# 默认目标
help:
	@echo "site-guard - Docker 管理命令"
	@echo ""
	@echo "使用方法: make [命令]"
	@echo ""
	@echo "可用命令:"
	@echo "  build    - 构建 Docker 镜像"
	@echo "  up       - 启动服务"
	@echo "  down     - 停止服务"
	@echo "  restart  - 重启服务"
	@echo "  logs     - 查看日志"
	@echo "  test     - 测试部署"
	@echo "  clean    - 清理容器和镜像"
	@echo "  backup   - 备份数据库"
	@echo "  shell    - 进入容器 Shell"
	@echo "  status   - 查看服务状态"
	@echo "  publish  - 发布镜像到远程仓库"
	@echo "             例: make publish REGISTRY=registry.cn-hangzhou.aliyuncs.com IMAGE_NAME=stanley-public/site-guard TAG=1.0.0"
	@echo ""

# 构建镜像
build:
	@echo "🔨 构建 Docker 镜像..."
	docker compose build

# 启动服务
up:
	@echo "🚀 启动服务..."
	@mkdir -p data logs
	docker compose up -d
	@echo "✅ 服务已启动"
	@echo "访问地址: http://localhost:1080"

# 停止服务
down:
	@echo "🛑 停止服务..."
	docker compose down
	@echo "✅ 服务已停止"

# 重启服务
restart:
	@echo "🔄 重启服务..."
	docker compose restart
	@echo "✅ 服务已重启"

# 查看日志
logs:
	docker compose logs -f

# 测试部署
test:
	@./docker/test.sh

# 清理容器和镜像
clean:
	@echo "🧹 清理容器和镜像..."
	docker compose down
	docker rmi site-guard:1.0.0 || true
	@echo "✅ 清理完成"

# 备份数据库
backup:
	@echo "💾 备份数据库..."
	@mkdir -p backups
	@timestamp=$$(date +%Y%m%d-%H%M%S); \
	tar -czf backups/backup-$$timestamp.tar.gz data/ && \
	echo "✅ 备份完成: backups/backup-$$timestamp.tar.gz"

# 进入容器 Shell
shell:
	docker exec -it site-guard sh

# 查看服务状态
status:
	@echo "📊 容器状态:"
	@docker ps -a | grep -E '\bsite-guard\b' || echo "容器未运行"
	@echo ""
	@echo "📊 进程状态:"
	@docker exec site-guard supervisorctl status || echo "无法获取进程状态"
	@echo ""
	@echo "📊 健康检查:"
	@curl -s http://localhost:1080/actuator/health || echo "服务未响应"

# 发布镜像到远程仓库
# 默认值：阿里云杭州区域 stanley-public 命名空间
publish:
	@echo "📤 发布镜像到远程仓库..."
	@REGISTRY?=registry.cn-hangzhou.aliyuncs.com
	@IMAGE_NAME?=stanley-public/site-guard
	@TAG?=1.0.0
	docker tag site-guard:1.0.0 $(REGISTRY)/$(IMAGE_NAME):$(TAG)
	docker push $(REGISTRY)/$(IMAGE_NAME):$(TAG)
	@echo "✅ 镜像已推送: $(REGISTRY)/$(IMAGE_NAME):$(TAG)"