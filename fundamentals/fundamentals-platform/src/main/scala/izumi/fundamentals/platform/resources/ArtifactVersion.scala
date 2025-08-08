package izumi.fundamentals.platform.resources

import izumi.fundamentals.platform.versions.Version

// TODO: full-scale version, with proper parsing & comparators
case class ArtifactVersion(version: Version) {
  override def toString: String = version.toString
}
