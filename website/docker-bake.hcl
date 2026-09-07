# Build targets consumed by docker/bake-action (see .github/workflows/docker.yml).
# Defining the build here keeps the Dockerfile, tags, cache config, and
# platform declarations in one file under the website/ source tree.
#
# The PR build target omits `cache-to` because fork pull requests don't have
# permission to write to the GHA cache; trying to export there fails the
# required check.
group "default" {
    targets = ["synapse-website"]
}

target "synapse-website" {
    context = "website"
    dockerfile = "Dockerfile"
    platforms = ["linux/amd64", "linux/arm64"]
    cache-from = ["type=gha"]
    cache-to   = ["type=gha,mode=max"]
    tags       = [""]
}

target "synapse-website-pr" {
    context = "website"
    dockerfile = "Dockerfile"
    platforms = ["linux/amd64", "linux/arm64"]
    cache-from = ["type=gha"]
    tags       = [""]
}
