package de.ragtractusx.ingest;

/**
 * A documentation file in the source repository.
 *
 * @param path    repo-relative path, e.g. {@code docs-kits/kits/connector-kit/adoption-view/adoption-view.md}
 * @param blobSha git blob SHA of the content; used to skip unchanged files on re-ingestion
 */
public record DocRef(String path, String blobSha) {
}
