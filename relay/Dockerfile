FROM python:3.12-slim
LABEL org.opencontainers.image.source="https://github.com/automationseDev/RelayHarbor-Public"
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server.py connections.py connection_transfer.py slack_adapter.py rss.py .
RUN useradd -r -u 10001 relay && mkdir -p /data && chown relay:relay /data
USER relay
EXPOSE 8000
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
