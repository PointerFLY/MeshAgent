#!/bin/bash

init() {
    CURRENT_DIR=$(pwd)
    IP_ADDR=127.0.0.1
    IMAGE_NAME=agent-demo
}

start_etcd() {
    PORT=2379
    ETCD_HOME=./etcd
    CLIENT_URL=http://$IP_ADDR:$PORT

    rm -rf $ETCD_HOME
    mkdir -p $ETCD_HOME
    nohup etcd \
          --listen-client-urls $CLIENT_URL \
          --advertise-client-urls $CLIENT_URL \
          --data-dir $ETCD_HOME/data > $ETCD_HOME/etcd.log 2>&1 &
    echo $! > $ETCD_HOME/run.pid
    sleep 1

    ATTEMPTS=0
    MAX_ATTEMPTS=5
    while true; do
        echo "Trying to connect $IP_ADDR:$PORT..."
        nc -v -n -w 1 $IP_ADDR $PORT < /dev/null
        if [[ $? -eq 0 ]]; then
            break
        fi
        if [[ $ATTEMPTS -eq $MAX_ATTEMPTS ]]; then
            echo "Cannot connect to port $PORT after $ATTEMPTS attempts."
            break
        fi
        ATTEMPTS=$((ATTEMPTS+1))
        echo "Waiting for 3 seconds... ($ATTEMPTS/$MAX_ATTEMPTS)"
        sleep 3
    done
}

build_docker_image() {
    docker build -t $IMAGE_NAME .
}

start_providers() {
    rm -rf provider-small
    mkdir -p provider-small/logs
    docker run -d \
        --name provider-small \
        --cidfile ./provider-small/run.cid \
        --cpu-period 50000 \
        --cpu-quota 30000 \
        -m 2g \
        -p 20889:20889 \
        -v $CURRENT_DIR/provider-small/logs:/root/logs \
        $IMAGE_NAME provider-small

    rm -rf provider-medium
    mkdir -p ./provider-medium/logs
    docker run -d \
        --name provider-medium \
        --cidfile ./provider-medium/run.cid \
        --cpu-period 50000 \
        --cpu-quota 60000 \
        -m 4g \
        -p 20890:20890 \
        -v $CURRENT_DIR/provider-medium/logs:/root/logs \
        $IMAGE_NAME provider-medium

    rm -rf provider-large
    mkdir -p provider-large/logs
    docker run -d \
        --name provider-large \
        --cidfile ./provider-large/run.cid \
        --cpu-period 50000 \
        --cpu-quota 90000 \
        -m 6g \
        -p 20891:20891 \
        -v $CURRENT_DIR/provider-large/logs:/root/logs \
        $IMAGE_NAME provider-large

    ATTEMPTS=0
    MAX_ATTEMPTS=5
    while true; do
        echo "Trying to connect provider-small 127.0.0.1:20889..."
        nc -v -n -w 1 127.0.0.1 20889 < /dev/null; r1=$?

        echo "Trying to connect provider-medium 127.0.0.1:20890..."
        nc -v -n -w 1 127.0.0.1 20890 < /dev/null; r2=$?

        echo "Trying to connect provider-large 127.0.0.1:20891..."
        nc -v -n -w 1 127.0.0.1 20891 < /dev/null; r3=$?

        echo $r1, $r2, $r3
        if [[ $r1 -eq 0 && $r2 -eq 0 && $r3 -eq 0 ]]; then
            break
        fi
        if [[ $ATTEMPTS -eq $MAX_ATTEMPTS ]]; then
            echo "Cannot connect to some of the ports after $ATTEMPTS attempts."
            break
        fi
        ATTEMPTS=$((ATTEMPTS+1))
        echo "Waiting for 3 seconds... ($ATTEMPTS/$MAX_ATTEMPTS)"
        sleep 3
    done
}

start_consumer() {
    rm -rf consumer
    mkdir -p consumer/logs
    docker run -d \
        --name consumer \
        --cidfile ./consumer/run.cid \
        --cpu-period 50000 \
        --cpu-quota 200000 \
        -m 4g \
        -p 8087:8087 \
        -v $CURRENT_DIR/consumer/logs:/root/logs \
        $IMAGE_NAME consumer

    ATTEMPTS=0
    MAX_ATTEMPTS=5
    while true; do
        echo "Trying to connect consumer 127.0.0.1:8087..."
        nc -v -n -w 1 127.0.0.1 8087 < /dev/null
        if [[ $? -eq 0 ]]; then
            break
        fi
        if [[ $ATTEMPTS -eq $MAX_ATTEMPTS ]]; then
            echo "Cannot connect to port 8087 after $ATTEMPTS attempts."
            break
        fi
        ATTEMPTS=$((ATTEMPTS+1))
        echo "Waiting for 3 seconds... ($ATTEMPTS/$MAX_ATTEMPTS)"
        sleep 3
    done
}

stress_test() {
    echo "Waiting container cool down."
    sleep 70

    wrk -t2 -c128 -d20 -T5 \
        --script=./wrk.lua \
        --latency http://127.0.0.1:8087/invoke

    sleep 5

    wrk -t2 -c256 -d20 -T5 \
        --script=./wrk.lua \
        --latency http://127.0.0.1:8087/invoke
}

clean_up() {
    CID_FILE=./consumer/run.cid
    CID=$(cat $CID_FILE)
    docker stop $CID
    docker logs $CID > ./consumer/logs/docker.log
    docker rm $CID
    rm -f $CID_FILE

    CID_FILE=./provider-small/run.cid
    CID=$(cat $CID_FILE)
    docker stop $CID
    docker logs $CID > ./provider-small/logs/docker.log
    docker rm $CID
    rm -f $CID_FILE

    CID_FILE=./provider-medium/run.cid
    CID=$(cat $CID_FILE)
    docker stop $CID
    docker logs $CID > ./provider-medium/logs/docker.log
    docker rm $CID
    rm -f $CID_FILE

    CID_FILE=./provider-large/run.cid
    CID=$(cat $CID_FILE)
    docker stop $CID
    docker logs $CID > ./provider-large/logs/docker.log
    docker rm $CID
    rm -f $CID_FILE

    PID_FILE=./etcd/run.pid
    PID=$(cat $PID_FILE)
    kill -9 $PID
    rm -f $PID_FILE
}

init
start_etcd
build_docker_image
start_providers
start_consumer
stress_test
clean_up
