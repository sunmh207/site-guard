#!/bin/sh
# site-guard - Docker 快速启动脚本

set -e

echo "=========================================="
echo "  site-guard - Docker 部署"
echo "=========================================="
echo ""

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "未找到 .env 文件，使用默认配置"
    echo "建议: 复制 .env.example 为 .env 并修改配置"
    echo ""
fi

# 创建必要的目录
echo "创建数据目录..."
mkdir -p data logs
chmod 755 data logs

# 构建镜像
echo ""
echo "构建 Docker 镜像..."
docker compose build

# 启动容器
echo ""
echo "启动容器..."
docker compose up -d

# 等待服务启动
echo ""
echo "等待服务启动..."
sleep 10

# 检查健康状态
echo ""
echo "检查服务健康状态..."
for i in 1 2 3 4 5 6 7 8 9 10; do
    if curl -fs http://localhost:1080/actuator/health > /dev/null 2>&1; then
        echo "服务启动成功！"
        echo ""
        echo "=========================================="
        echo "  访问地址"
        echo "=========================================="
        echo "前端应用: http://localhost:1080"
        echo "后端 API: http://localhost:1080/api/v1"
        echo "Swagger:  http://localhost:1080/swagger-ui.html"
        echo "健康检查: http://localhost:1080/actuator/health"
        echo ""
        echo "=========================================="
        echo "  管理命令"
        echo "=========================================="
        echo "查看日志: docker compose logs -f"
        echo "停止服务: docker compose down"
        echo "重启服务: docker compose restart"
        echo ""
        exit 0
    fi
    echo "等待中... ($i/10)"
    sleep 5
done

echo "服务启动超时，请检查日志："
echo "docker compose logs -f"
exit 1