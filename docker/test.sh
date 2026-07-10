#!/bin/sh
# site-guard - 部署测试脚本

set -e

echo "=========================================="
echo "  site-guard - 部署测试"
echo "=========================================="
echo ""

# 检查容器是否运行
echo "1. 检查容器状态..."
if ! docker ps | grep -qE '\bsite-guard\b'; then
    echo "容器未运行，请先启动服务"
    echo "   运行: docker compose up -d"
    exit 1
fi
echo "容器正在运行"
echo ""

# 检查健康端点
echo "2. 检查健康端点..."
if curl -s http://localhost:1080/actuator/health | grep -q "UP"; then
    echo "健康检查通过"
else
    echo "健康检查失败"
    exit 1
fi
echo ""

# 检查前端
echo "3. 检查前端服务..."
if curl -s -o /dev/null -w "%{http_code}" http://localhost:1080 | grep -q "200"; then
    echo "前端服务正常"
else
    echo "前端服务异常"
    exit 1
fi
echo ""

# 检查 Swagger UI
echo "4. 检查 Swagger UI..."
# -L 跟随 302 重定向（/swagger-ui.html → /swagger-ui/index.html）
if curl -sL -o /dev/null -w "%{http_code}" http://localhost:1080/swagger-ui.html | grep -q "200"; then
    echo "Swagger UI 正常"
else
    echo "Swagger UI 可能未启用"
fi
echo ""

# 检查 OpenAPI 文档
echo "5. 检查 OpenAPI 文档..."
if curl -s -o /dev/null -w "%{http_code}" http://localhost:1080/v3/api-docs | grep -q "200"; then
    echo "OpenAPI 文档正常"
else
    echo "OpenAPI 文档未响应"
fi
echo ""

# 检查数据目录
echo "6. 检查数据持久化..."
if [ -d "data" ]; then
    echo "数据目录存在"
else
    echo "数据目录不存在"
fi
echo ""

# 检查日志目录
echo "7. 检查日志目录..."
if [ -d "logs" ]; then
    echo "日志目录存在"
else
    echo "日志目录不存在"
fi
echo ""

# 检查进程状态
echo "8. 检查容器内进程..."
docker exec site-guard supervisorctl status || true
echo ""

echo "=========================================="
echo "  测试完成"
echo "=========================================="
echo ""
echo "服务访问地址："
echo "   前端: http://localhost:1080"
echo "   API:  http://localhost:1080/api/v1"
echo "   文档: http://localhost:1080/swagger-ui.html"
echo ""