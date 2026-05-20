package net.oculus.shaderpack.include;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Straight copy of the Iris absolute pack path helper, pared down for the
 * 1.12.2 backport. It normalises shader pack paths and exposes simple helpers
 * used by ProgramSet when locating GLSL sources.
 */
public final class AbsolutePackPath {
	private final String path;

	private AbsolutePackPath(String absolute) {
		this.path = absolute;
	}

	public static AbsolutePackPath fromAbsolutePath(String absolutePath) {
		return new AbsolutePackPath(normalizeAbsolutePath(absolutePath));
	}

	public Optional<AbsolutePackPath> parent() {
		if (path.equals("/")) {
			return Optional.empty();
		}

		int lastSlash = path.lastIndexOf('/');

		return Optional.of(new AbsolutePackPath(path.substring(0, lastSlash)));
	}

	public AbsolutePackPath resolve(String child) {
		if (child.startsWith("/")) {
			return fromAbsolutePath(child);
		}

		String merged;

		if (!this.path.endsWith("/") && !child.startsWith("/")) {
			merged = this.path + "/" + child;
		} else {
			merged = this.path + child;
		}

		return fromAbsolutePath(merged);
	}

	public Path resolved(Path root) {
		if (path.equals("/")) {
			return root;
		}

		return root.resolve(path.substring(1));
	}

	private static String normalizeAbsolutePath(String path) {
		if (!path.startsWith("/")) {
			throw new IllegalArgumentException("Not an absolute path: " + path);
		}

		String[] segments = path.split(Pattern.quote("/"));
		List<String> parsed = new ArrayList<>();

		for (String segment : segments) {
			if (segment.isEmpty() || segment.equals(".")) {
				continue;
			}

			if (segment.equals("..")) {
				if (!parsed.isEmpty()) {
					parsed.remove(parsed.size() - 1);
				}
			} else {
				parsed.add(segment);
			}
		}

		if (parsed.isEmpty()) {
			return "/";
		}

		StringBuilder normalised = new StringBuilder();
		for (String segment : parsed) {
			normalised.append('/').append(segment);
		}

		return normalised.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof AbsolutePackPath)) {
			return false;
		}
		AbsolutePackPath other = (AbsolutePackPath) obj;
		return Objects.equals(path, other.path);
	}

	@Override
	public int hashCode() {
		return Objects.hash(path);
	}

	@Override
	public String toString() {
		return "AbsolutePackPath {" + path + "}";
	}

	public String getPathString() {
		return path;
	}
}
