package com.snap.camerakit.jenkins.pipeline

import static org.junit.Assert.assertEquals

import org.junit.Test

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

class StateTest {

    @Test
    void toJson_fromJson_equals() {
        def state = new State()

        state.stage1.releaseVerificationIssueKey = 'some_key'
        state.stage1.releaseCoordinationSlackChannel = '#some_channel'
        state.stage1.releaseVersion = Version.from('1.0.9')
        state.stage1.releaseScope = ReleaseScope.MINOR

        state.stage2.developmentVersion = Version.from('1.1.0')

        state.stage3.developmentAndroidSdkBuild = new SdkBuild(
                Version.from('1.1.0+e73de509.854'),
                'master',
                'e73de50929a2e992af48f6ffd50f8a0c34aec4b8',
                854,
                'snap-sdk-android-publish',
                'snapengine-builder.sc-corp.net'
        )
        state.stage3.releaseCandidateAndroidSdkBuild = new SdkBuild(
                Version.from('1.0.9-rc1+855'),
                'camerakit/release/1.0.x',
                '8c9efe912dbdc63d1714aba1f2dbee3745b6da33',
                855,
                'snap-sdk-android-publish',
                'snapengine-builder.sc-corp.net'
        )
        state.stage3.developmentIosSdkBuild = new SdkBuild(
                Version.from('1.1.0'),
                'master',
                '090235ba7fa03cf2d9790c2b84e7f1a14017dde0',
                72243,
                'camera-kit-ios-sdk',
                'snapengine-builder.sc-corp.net'
        )
        state.stage3.releaseCandidateIosSdkBuild = new SdkBuild(
                Version.from('1.0.9'),
                'camerakit/release/1.0.x',
                'b78695c6f2aa93c4a94f5a4beedd2cc50caacbd4',
                855,
                'camera-kit-ios-sdk',
                'snapengine-builder.sc-corp.net'
        )

        state.stage4.releaseCandidateBinaryBuilds = [
                'SDK distribution Android sample app build' : new BinaryBuild(
                        Version.from('1.0.9'),
                        'release/1.0.x',
                        '11df4c73001d2db8d702cdfb720e42ffe0f405b2',
                        3331,
                        'camerakit-distribution-android-publish',
                        'snapengine-builder.sc-corp.net',
                        'https://install.appcenter.ms/orgs/app-2q6u/apps/CameraKit-Sample-Partner/releases/1011',
                        null
                ),
                'SDK distribution build' : new BinaryBuild(
                        Version.from('1.0.9'),
                        'release/1.0.x',
                        '11df4c73001d2db8d702cdfb720e42ffe0f405b2',
                        4127,
                        'camerakit-distribution-build',
                        'snapengine-builder.sc-corp.net',
                        'https://console.cloud.google.com/storage/browser/_details/snapengine-builder-artifacts/camerakit-distribution-build/4127/camerakit-distribution.zip',
                        'gs://snapengine-builder-artifacts/camerakit-distribution-build/4127/camerakit-distribution.zip'
                ),
                'SDK distribution iOS sample app build' : new BinaryBuild(
                        Version.from('1.0.9'),
                        'release/1.0.x',
                        '11df4c73001d2db8d702cdfb720e42ffe0f405b2',
                        3329,
                        'camerakit-distribution-ios-publish',
                        'snapengine-builder.sc-corp.net',
                        'https://install.appcenter.ms/orgs/app-2q6u/apps/CameraKit-Sample-Partner-iOS/releases/925',
                        null
                ),
        ]

        state.stage5.releaseVerificationComplete = true
        state.stage5.releaseCandidateAndroidSdkBuild = new SdkBuild(
                Version.from('1.0.9-rc2'),
                'camerakit/release/1.0.x',
                'ee68b1edb0f063b662099121b93fcc55c672a311',
                860,
                'snap-sdk-android-publish',
                'snapengine-builder.sc-corp.net'
        )
        state.stage5.releaseCandidateIosSdkBuild = new SdkBuild(
                Version.from('1.0.9'),
                'camerakit/release/1.0.x',
                '92d368c13f85178432f991adb491fdbde5fe7c11',
                899,
                'camera-kit-ios-sdk',
                'snapengine-builder.sc-corp.net'
        )
        state.stage5.releaseCandidateBinaryBuilds = [
                'SDK distribution Android sample app build' : new BinaryBuild(
                        Version.from('1.0.9'),
                        'release/1.0.x',
                        'a63b6dfac92d307559170fd044ed2c9c63fd1cd9',
                        3332,
                        'camerakit-distribution-android-publish',
                        'snapengine-builder.sc-corp.net',
                        'https://install.appcenter.ms/orgs/app-2q6u/apps/CameraKit-Sample-Partner/releases/1012',
                        null
                ),
                'SDK distribution build' : new BinaryBuild(
                        Version.from('1.0.9'),
                        'release/1.0.x',
                        'a63b6dfac92d307559170fd044ed2c9c63fd1cd9',
                        4128,
                        'camerakit-distribution-build',
                        'snapengine-builder.sc-corp.net',
                        'https://console.cloud.google.com/storage/browser/_details/snapengine-builder-artifacts/camerakit-distribution-build/4128/camerakit-distribution.zip',
                        'gs://snapengine-builder-artifacts/camerakit-distribution-build/4127/camerakit-distribution.zip'
                ),
                'SDK distribution iOS sample app build' : new BinaryBuild(
                        Version.from('1.0.9'),
                        'release/1.0.x',
                        'a63b6dfac92d307559170fd044ed2c9c63fd1cd9',
                        3330,
                        'camerakit-distribution-ios-publish',
                        'snapengine-builder.sc-corp.net',
                        'https://install.appcenter.ms/orgs/app-2q6u/apps/CameraKit-Sample-Partner-iOS/releases/926',
                        null
                )
        ]

        state.stage6.releaseAndroidSdkBuild = new SdkBuild(
                Version.from('1.0.9'),
                'camerakit/release/1.0.x',
                'bd9d4e1be4cca3bd0ab160c11c94deb26bf771ab',
                862,
                'snap-sdk-android-publish',
                'snapengine-builder.sc-corp.net'
        )
        state.stage6.releaseAndroidSdkBuild = new SdkBuild(
                Version.from('1.0.9'),
                'camerakit/release/1.0.x',
                '1cb6e0805486000feba571f4ca039ab2972d8d18',
                901,
                'camera-kit-ios-sdk',
                'snapengine-builder.sc-corp.net'
        )

        state.stage8.releaseGithubUrl =
                "https://github.sc-corp.net/Snapchat/camera-kit-distribution/releases/tag/untagged-87e08cd11e1b3ab87dd2"
        state.stage8.releaseBinaryBuilds = [
                'SDK distribution Android sample app build' : new BinaryBuild(
                        Version.from('1.0.9'),
                        'release/1.0.x',
                        '1cb6e0805486000feba571f4ca039ab2972d8d18',
                        3335,
                        'camerakit-distribution-android-publish',
                        'snapengine-builder.sc-corp.net',
                        'https://install.appcenter.ms/orgs/app-2q6u/apps/CameraKit-Sample-Partner/releases/1013',
                        null
                ),
                'SDK distribution build' : new BinaryBuild(
                        Version.from('1.0.9'),
                        'release/1.0.x',
                        '1cb6e0805486000feba571f4ca039ab2972d8d18',
                        4129,
                        'camerakit-distribution-build',
                        'snapengine-builder.sc-corp.net',
                        'https://console.cloud.google.com/storage/browser/_details/snapengine-builder-artifacts/camerakit-distribution-build/4129/camerakit-distribution.zip',
                        'gs://snapengine-builder-artifacts/camerakit-distribution-build/4129/camerakit-distribution.zip'
                ),
                'SDK distribution iOS sample app build' : new BinaryBuild(
                        Version.from('1.0.9'),
                        'release/1.0.x',
                        '1cb6e0805486000feba571f4ca039ab2972d8d18',
                        3338,
                        'camerakit-distribution-ios-publish',
                        'snapengine-builder.sc-corp.net',
                        'https://install.appcenter.ms/orgs/app-2q6u/apps/CameraKit-Sample-Partner-iOS/releases/927',
                        null
                )
        ]

        state.stage9.iosSdkPublishedToCocoapods = true
        state.stage9.androidSdkPublishedToMavenCentral = true

        state.stage10.sdkReferenceSyncedToPublicGithub = true
        state.stage10.sdkApiReferenceSyncedToSnapDocs = true

        def json = state.toString()
        def deserializedState = State.fromJson(json)

        assertEquals(state, deserializedState)
    }

    @Test
    void fromJson_expected() {
        def json = getClass().getResourceAsStream('/state_example_1.json').text
        def state = State.fromJson(json)

        assertEquals(Version.from("1.22.0"), state.stage1.releaseVersion)
        assertEquals("#camkit-4267-release-1-22-0", state.stage1.releaseCoordinationSlackChannel)
        assertEquals("CAMKIT-4267", state.stage1.releaseVerificationIssueKey)
        assertEquals(ReleaseScope.MINOR, state.stage1.releaseScope)

        assertEquals(Version.from("1.23.0"), state.stage2.developmentVersion)

        assertNotNull(state.stage3.releaseCandidateAndroidSdkBuild)
        assertNotNull(state.stage3.releaseCandidateIosSdkBuild)
        assertNotNull(state.stage3.developmentAndroidSdkBuild)
        assertNotNull(state.stage3.developmentIosSdkBuild)

        assertNull(state.stage4.releaseCandidateBinaryBuildsCommitSha)
        assertTrue(state.stage4.releaseCandidateBinaryBuilds.isEmpty())

        assertNull(state.stage5.releaseCandidateIosSdkBuild)
        assertNull(state.stage5.releaseCandidateIosSdkBuild)
        assertTrue(state.stage5.releaseVerificationComplete)
        assertTrue(state.stage5.releaseCandidateBinaryBuilds.isEmpty())

        assertNotNull(state.stage6.releaseAndroidSdkBuild)
        assertNotNull(state.stage6.releaseIosSdkBuild)

        assertTrue(!state.stage8.releaseBinaryBuilds.isEmpty())
        assertEquals(
                "https://github.sc-corp.net/Snapchat/camera-kit-distribution/releases/tag/untagged-87e08cd11e1b3ab87dd2",
                state.stage8.releaseGithubUrl
        )

        assertTrue(state.stage9.androidSdkPublishedToMavenCentral)
        assertTrue(state.stage9.iosSdkPublishedToCocoapods)

        assertTrue(state.stage10.sdkApiReferenceSyncedToSnapDocs)
        assertTrue(state.stage10.sdkReferenceSyncedToPublicGithub)
    }
}
