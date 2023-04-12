package com.snap.camerakit.jenkins.pipeline

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

import org.junit.Test

class VersionTest {

   @Test
   void fromString_qualifierWithBuildMetadata() {
      Version version = Version.from("1.23.0+6ce970ae.829");

      assertNotNull(version);
      assertEquals(1, version.getMajor());
      assertEquals(23, version.getMinor());
      assertEquals(0, version.getPatch());
      assertEquals("+6ce970ae.829", version.getQualifier());
   }
}
