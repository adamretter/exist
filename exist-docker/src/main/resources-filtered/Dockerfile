FROM openjdk:8-jdk-slim as builder

# Install tools required by FOP
WORKDIR /usr/local
RUN apt-get update && apt-get install -y --no-install-recommends \
  expat \
  fontconfig \
  libfreetype6 \
  liblcms2-2 \
  libpng16-16 \
  ttf-dejavu-core

FROM gcr.io/distroless/java

# Build-time metadata as defined at http://label-schema.org
LABEL org.label-schema.build-date=${build-tstamp} \
      org.label-schema.description="${project.description}" \
      org.label-schema.name="existdb" \
      org.label-schema.schema-version="1.0" \
      org.label-schema.url="${project.url}" \
      org.label-schema.vcs-ref=${build-commit-abbrev} \
      org.label-schema.vcs-url="${project.scm.url}" \
      org.label-schema.vendor="existdb"

# make CACHE_MEM and MAX_BROKER available to users
ARG CACHE_MEM
ARG MAX_BROKER

ENV EXIST_HOME  "/exist"
ENV JAVA_TOOL_OPTIONS \
  -Dfile.encoding=UTF8 \
  -Dsun.jnu.encoding=UTF-8 \
  -Djava.awt.headless=true \
  -Dorg.exist.db-connection.cacheSize=${CACHE_MEM:-256}M \
  -Dorg.exist.db-connection.pool.max=${MAX_BROKER:-20} \
  -Dlog4j.configurationFile=/exist/etc/log4j2.xml \
  -Dexist.home=/exist \
  -Dexist.configurationFile=/exist/etc/conf.xml \
  -Djetty.home=/exist \
  -Dexist.jetty.config=/exist/etc/jetty/standard.enabled-jetty-configs \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+UseCGroupMemoryLimitForHeap \
  -XX:+UseG1GC \
  -XX:+UseStringDeduplication \
  -XX:MaxRAMFraction=1

# Copy over dependancies for Apache FOP, missing from gcr's JRE
COPY --from=builder /usr/lib/x86_64-linux-gnu/libfreetype.so.6.12.3 /usr/lib/x86_64-linux-gnu/libfreetype.so.6
COPY --from=builder /usr/lib/x86_64-linux-gnu/liblcms2.so.2.0.8 /usr/lib/x86_64-linux-gnu/liblcms2.so.2
COPY --from=builder /usr/lib/x86_64-linux-gnu/libpng16.so.16.28.0 /usr/lib/x86_64-linux-gnu/libpng16.so.16
COPY --from=builder /usr/lib/x86_64-linux-gnu/libfontconfig.so.1.8.0 /usr/lib/x86_64-linux-gnu/libfontconfig.so.1


# Copy dependancies for Apache Batik (used by Apache FOP to handle SVG rendering)
COPY --from=builder /etc/fonts /etc/fonts
COPY --from=builder /lib/x86_64-linux-gnu/libexpat.so.1 /lib/x86_64-linux-gnu/libexpat.so.1
COPY --from=builder /usr/share/fontconfig /usr/share/fontconfig
COPY --from=builder /usr/share/fonts/truetype/dejavu /usr/share/fonts/truetype/dejavu

WORKDIR $EXIST_HOME
# DP can't test this right now without db/apps but this might need to go before all COPYs

COPY LICENSE $EXIST_HOME/LICENSE
COPY autodeploy $EXIST_HOME/autodeploy
COPY bin $EXIST_HOME/bin
COPY etc $EXIST_HOME/etc
COPY lib $EXIST_HOME/lib
COPY logs $EXIST_HOME/logs

EXPOSE 8080 8443


HEALTHCHECK CMD [ "java", \
    "-classpath", \
    "/exist/etc:/exist/lib/appassembler-booter-2.0.1-SNAPSHOT.jar:/exist/lib/appassembler-model-2.0.1-SNAPSHOT.jar:/exist/lib/plexus-utils-3.0.24.jar:/exist/lib/stax-api-1.0.1.jar:/exist/lib/stax-1.1.1-dev.jar", \
    "-Dapp.name=client", \
    "-Dapp.repo=/exist/lib", \
    "-Dapp.home=/exist", \
    "-Dbasedir=/exist", \
    "org.codehaus.mojo.appassembler.booter.AppassemblerBooter", \
    "--no-gui",  "--xpath", "system:get-version()" ]

ENTRYPOINT [ "java", \
    "-classpath", \
    "/exist/etc:/exist/lib/appassembler-booter-2.0.1-SNAPSHOT.jar:/exist/lib/appassembler-model-2.0.1-SNAPSHOT.jar:/exist/lib/plexus-utils-3.0.24.jar:/exist/lib/stax-api-1.0.1.jar:/exist/lib/stax-1.1.1-dev.jar", \
    "-Dapp.name=startup", \
    "-Dapp.repo=/exist/lib", \
    "-Dapp.home=/exist", \
    "-Dbasedir=/exist", \
    "org.codehaus.mojo.appassembler.booter.AppassemblerBooter" ]