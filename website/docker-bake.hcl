# Build targets consumed by docker/bake-action (see .github/workflows/docker.yml).
# Defining the build here keeps the Dockerfile, tags, cache config, and platform
# declarations in one file under the website/ source tree.
group "default" {
    targets = ["synapse-website"]
}

target "synapse-website" {
    context = "."
    dockerfile = "Dockerfile"
    platforms = ["linux/amd64", "linux/arm64"]
    tags = [""]
}
