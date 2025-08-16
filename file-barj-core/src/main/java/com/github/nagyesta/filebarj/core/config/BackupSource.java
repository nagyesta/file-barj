package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.nagyesta.filebarj.core.model.BackupPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a backup source root. Can match a file or directory.
 */
@JsonDeserialize(builder = BackupSource.BackupSourceBuilder.class)
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupSource {
    /**
     * The path we want to back up. Can be file or directory.
     */
    @Getter
    @JsonProperty("path")
    private final @Valid
    @NonNull BackupPath path;
    /**
     * Optional include patterns for filtering the contents. Uses {@link java.nio.file.PathMatcher}
     * with "glob" syntax relative to the value of the path field.
     */
    @Getter
    @JsonProperty("include_patterns")
    private final Set<@NotNull @NotBlank String> includePatterns;
    /**
     * Optional exclude patterns for filtering the contents. Uses {@link java.nio.file.PathMatcher}
     * with "glob" syntax relative to the value of the path field.
     */
    @Getter
    @JsonProperty("exclude_patterns")
    private final Set<@NotNull @NotBlank String> excludePatterns;

    BackupSource(
            final @Valid @NonNull BackupPath path,
            final Set<@NotNull @NotBlank String> includePatterns,
            final Set<@NotNull @NotBlank String> excludePatterns) {
        this.path = path;
        this.includePatterns = Optional.ofNullable(includePatterns).map(Set::copyOf).orElse(Collections.emptySet());
        this.excludePatterns = Optional.ofNullable(excludePatterns).map(Set::copyOf).orElse(Collections.emptySet());
    }

    public static BackupSourceBuilder builder() {
        return new BackupSourceBuilder();
    }

    @Override
    public String toString() {
        return "BackupSource{path=" + path + ", include=" + includePatterns + ", exclude=" + excludePatterns + "}";
    }

    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PACKAGE)
    @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
    public static class BackupSourceBuilder {
        private BackupPath path;
        private Set<String> includePatterns;
        private Set<String> excludePatterns;

        @JsonProperty("path")
        public BackupSourceBuilder path(final @Valid @NonNull BackupPath path) {
            this.path = path;
            return this;
        }

        @JsonProperty("include_patterns")
        public BackupSourceBuilder includePatterns(final Set<@NotNull @NotBlank String> includePatterns) {
            this.includePatterns = includePatterns;
            return this;
        }

        @JsonProperty("exclude_patterns")
        public BackupSourceBuilder excludePatterns(final Set<@NotNull @NotBlank String> excludePatterns) {
            this.excludePatterns = excludePatterns;
            return this;
        }

        public BackupSource build() {
            return new BackupSource(this.path, this.includePatterns, this.excludePatterns);
        }
    }
}
