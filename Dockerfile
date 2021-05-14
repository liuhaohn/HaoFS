FROM java:my

COPY ./build/libs/hao-blockchain-1.0.0-RELEASE.jar /root/
WORKDIR /root/

VOLUME /root/conf
VOLUME /root/filesystem

CMD java -jar hao-blockchain-1.0.0-RELEASE.jar