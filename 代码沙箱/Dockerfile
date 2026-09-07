FROM docker:27-cli

# One self-contained image is used for the internal API and its short-lived
# workers. Workers never receive the Docker socket or any host bind mount.
RUN apk add --no-cache \
    python3 \
    openjdk17-jdk \
    build-base \
    go

WORKDIR /app
COPY server.py runner.py /app/

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

EXPOSE 8090
ENTRYPOINT ["python3", "/app/server.py"]
