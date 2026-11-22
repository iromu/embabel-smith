# builder-publish-location

A git publish reports where the artifact lives in the source's own terms instead of a directory inside the publisher's temporary clone, which close() deletes — so the build report's published: line and the studio's publish response name a location that still means something after the publisher is gone. Covers a skill published over git: with an offline test, which nothing does today.
