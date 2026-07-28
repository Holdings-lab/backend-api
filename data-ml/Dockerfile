FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y \
  gcc \
  curl \
  chromium \
  chromium-driver \
  libnss3 \
  libxss1 \
  libasound2 \
  libatk-bridge2.0-0 \
  libgtk-3-0 \
  fonts-liberation \
  && rm -rf /var/lib/apt/lists/*

ENV CHROME_BIN=/usr/bin/chromium
ENV CHROMEDRIVER_PATH=/usr/bin/chromedriver

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt \
  && playwright install --with-deps chromium

COPY . .

ENV PYTHONUNBUFFERED=1
ENV ML_API_PORT=9000

EXPOSE 9000

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:9000/ml/health || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "9000"]
