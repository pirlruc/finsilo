FROM gcr.io/oss-fuzz-base/base-builder-jvm@sha256:bd13a763de1a0fb22099442094e7e0049657a8c47f01e9d7af8bb849e0ad4699
COPY . $SRC/finsilo
COPY .clusterfuzzlite/build.sh $SRC/build.sh
WORKDIR $SRC/finsilo
