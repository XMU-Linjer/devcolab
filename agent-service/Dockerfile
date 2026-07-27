FROM python:3.12-slim

WORKDIR /app
COPY pyproject.toml README.md ./
RUN mkdir app \
    && touch app/__init__.py \
    && pip install --no-cache-dir . \
    && rm -rf app
COPY app ./app

EXPOSE 8092
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8092"]
