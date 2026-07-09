FROM scratch
USER 65532:65532
HEALTHCHECK NONE

# ClusterFuzzLite discovery marker for Scorecard. The actual Jazzer entrypoint
# is src/test/java/com/clearfolio/viewer/controller/FuzzDownloadFilename.java;
# this marker stays buildable when central coverage checks build .clusterfuzzlite
# as the Docker context.
