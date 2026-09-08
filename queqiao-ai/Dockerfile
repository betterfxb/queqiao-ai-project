# 鹊桥 AI 服务镜像
FROM python:3.11-slim

# 时区与基础工具
ENV TZ=Asia/Shanghai \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# 先装依赖（利用层缓存）
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 拷贝源码
COPY . .

# 监听所有网卡，容器间通过服务名访问
EXPOSE 8090

CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port 8090 --workers 2"]
