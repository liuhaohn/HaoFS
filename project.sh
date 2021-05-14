#!/bin/bash

function printHelp() {
  echo "Usage: "
  echo "  project.sh <Mode> [Flags]"
  echo
  echo "    <Mode>:"
  echo "       delete   - delete the filesystem folder"
  echo "       copy     - copy conf folder"
  echo "       up       - start up"
  echo "       upd      - background start up"
  echo "       down     - shutdown project"
  echo "       restart  - restart project"
  echo
  echo "    [Flags]:"
  echo "      -df1      - delete filesystem folder 1"
  echo "      -df2      - delete filesystem folder 2"
  echo "      -df3      - delete filesystem folder 3"
  echo "      -dfa      - delete the filesystem* folder"
  echo "      -n1       - start node 1"
  echo "      -n2       - start node 2"
  echo "      -n3       - start node 3"
  echo "      -na       - start node 1-3"
  echo "      -img      - regenerate image by Dockerfile"
  echo "      --fabric <ip>      - specifies the ip address of the fabric network"
  echo
  echo "    Examples:"
  echo "      ./project.sh upd -na -img -dfa   # Recompile to generate the image, delete the file system folder, and start node 1-3 in the background"
}

function copyFile() {
  scp hao@fabric:/home/hao/IdeaProjects/fabric-kiftd-gradle/wallet/* ./conf/
  scp hao@fabric:/home/hao/IdeaProjects/fabric-kiftd-gradle/gateway/* ./conf/
  scp hao@fabric:/home/hao/IdeaProjects/fabric-kiftd-gradle/test-network/organizations/peerOrganizations/org1.example.com/ca/* ./conf
  scp hao@fabric:/home/hao/IdeaProjects/fabric-kiftd-gradle/test-network/organizations/peerOrganizations/org2.example.com/ca/* ./conf
  scp hao@fabric:/home/hao/IdeaProjects/fabric-kiftd-gradle/test-network/organizations/peerOrganizations/org3.example.com/ca/* ./conf

  mv ./conf/connection-org1.yaml ./conf/connection-org1-tmp.yaml
  mv ./conf/connection-org2.yaml ./conf/connection-org2-tmp.yaml
  mv ./conf/connection-org3.yaml ./conf/connection-org3-tmp.yaml

  sed -e "s/localhost:7051/peer0.org1.example.com:7051/g" \
    -e "s/localhost:7054/ca.org1.example.com:7054/g" \
    ./conf/connection-org1-tmp.yaml >./conf/connection-org1.yaml
  sed -e "s/localhost:9051/peer0.org2.example.com:9051/g" \
    -e "s/localhost:8054/ca.org2.example.com:8054/g" \
    ./conf/connection-org2-tmp.yaml >./conf/connection-org2.yaml
  sed -e "s/localhost:11051/peer0.org3.example.com:11051/g" \
    -e "s/localhost:11054/ca.org3.example.com:11054/g" \
    ./conf/connection-org3-tmp.yaml >./conf/connection-org3.yaml
  rm -rf ./conf/*-tmp.yaml

  cp ./conf/Visito*.id ./conf2/
  cp ./conf/Visito*.id ./conf3/

  mv ./conf/Admin*org2*.id ./conf2/
  mv ./conf/User*org2*.id ./conf2/
  mv ./conf/Admin*org3*.id ./conf3/
  mv ./conf/User*org3*.id ./conf3/

  cp ./conf/*.pem ./conf2/
  cp ./conf/*.pem ./conf3/

  mv ./conf/*org2.yaml ./conf2
  mv ./conf/*org3.yaml ./conf3
}

function deleteFile() {
  if ${DF1}; then
    echo delete filesystem
    sudo rm -rf filesystem/*
  fi

  if ${DF2}; then
    echo delete filesystem2
    sudo rm -rf filesystem2/*
  fi

  if ${DF3}; then
    echo delete filesystem3
    sudo rm -rf filesystem3/*
  fi
}

function projectDown() {
  docker-compose down
  deleteFile
}

function projectUp() {
  deleteFile

  if ${IMG}; then
    docker rmi filenode:1.0
    ./gradlew bootJar
    docker build -f ./Dockerfile -t filenode:1.0 .
  fi

  copyFile
  if ${N1}; then
    docker-compose up ${D} filenode.org1.hao.com
    sleep 30
  fi
  if ${N2}; then
    export CMD2='java -jar hao-blockchain-1.0.0-RELEASE.jar'
    docker-compose up ${D} filenode.org2.hao.com
    sleep 30
  fi
  if ${N3}; then
    export CMD3='java -jar hao-blockchain-1.0.0-RELEASE.jar'
    docker-compose up ${D} filenode.org3.hao.com
  fi

  if ${NA}; then
    export CMD2='./wait-for-it.sh filenode.org1.hao.com:8081 -t 60 -- java -jar hao-blockchain-1.0.0-RELEASE.jar'
    export CMD3='./wait-for-it.sh filenode.org2.hao.com:8082 -t 120 -- java -jar hao-blockchain-1.0.0-RELEASE.jar'
    docker-compose up ${D}
  fi
}

function wait_for() {
  WAITFORIT_start_ts=$(date +%s)
  sleep 5
  WAITFORIT_HOST=$1
  WAITFORIT_PORT=$2
  while :; do
    nc -z $WAITFORIT_HOST $WAITFORIT_PORT
    WAITFORIT_result=$?
    if [[ $WAITFORIT_result -eq 0 ]]; then
      WAITFORIT_end_ts=$(date +%s)
      echo "$WAITFORIT_HOST:$WAITFORIT_PORT is available after $((WAITFORIT_end_ts - WAITFORIT_start_ts)) seconds"
      break
    fi
    sleep 2
  done
}

MODE=""
DF1=false
DF2=false
DF3=false
N1=false
N2=false
N3=false
NA=false
IMG=false
D=""
export FABRIC="210.43.57.6"

## Parse mode
if [[ $# -lt 1 ]]; then
  printHelp
  exit 0
else
  MODE=$1
  shift
fi

# parse flags
while [[ $# -ge 1 ]]; do
  key="$1"
  case $key in
  -h)
    printHelp
    exit 0
    ;;
  -df1)
    DF1=true
    ;;
  -df2)
    DF2=true
    ;;
  -df3)
    DF3=true
    ;;
  -dfa)
    DF1=true
    DF2=true
    DF3=true
    ;;
  -n1)
    N1=true
    ;;
  -n2)
    N2=true
    ;;
  -n3)
    N3=true
    ;;
  -na)
    NA=true
    ;;
  -img)
    IMG=true
    ;;
  --fabric)
    export FABRIC="$2"
    shift
    ;;
  *)
    echo
    echo "Unknown flag: $key"
    echo
    printHelp
    exit 1
    ;;
  esac
  shift
done

if [ "${MODE}" == "delete" ]; then
  deleteFile
elif [ "${MODE}" == "copy" ]; then
  copyFile
elif [ "${MODE}" == "up" ]; then
  projectUp
elif [ "${MODE}" == "upd" ]; then
  D="-d"
  projectUp
elif [ "${MODE}" == "down" ]; then
  projectDown
elif [ "${MODE}" == "restart" ]; then
  projectDown
  projectUp
else
  printHelp
  exit 1
fi
