package com.snap.camerakit.jenkins.pipeline

import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.util.concurrent.Semaphore

class CameraKitReleasePipelineTest extends DeclarativePipelineTest {

    private static final long DEFAULT_BUILD_JOB_NUMBER = 101L
    private static final String DEFAULT_JIRA_RELEASE_VERIFICATION_ISSUE_KEY = 'CAMKIT-4226'
    private static final String DEFAULT_GIT_COMMIT_SHA = 'c13e2ac6d9cda0e4cbf4fa4ce700a4e39a936c69'

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Override
    @Before
    void setUp() throws Exception {
        super.setUp()

        def lock = new Semaphore(1)
        helper.registerAllowedMethod('lock', [String, Closure], { args ->
            lock.acquire()
            try {
                (args[1] as Closure).call()
            } finally {
                lock.release()
            }
        })
        helper.registerAllowedMethod('preserveStashes', { args ->
            args
        })
        helper.registerAllowedMethod('input', [Map], { args ->
            'minor'
        })
        helper.registerAllowedMethod('git', [Map], { args ->
            args
        })
        helper.registerAllowedMethod('waitUntil', [Map, Closure], { args ->
            def closure = args[1]
            def condition = false
            while (!condition) {
                condition = closure.call() as boolean
            }

        })
        helper.registerAllowedMethod("build", [Map.class], {
            [
                    number: DEFAULT_BUILD_JOB_NUMBER,
                    buildVariables: ['GIT_COMMIT' : DEFAULT_GIT_COMMIT_SHA]
            ]
        })

        helper.registerAllowedMethod('sh', [Map], { args ->
            String script = args['script']
            if (script != null && !script.isEmpty()) {
                if (script.contains('to-jira-dot-sc-ats')) {
                    return """{ 
                        "key": "$DEFAULT_JIRA_RELEASE_VERIFICATION_ISSUE_KEY", 
                        "fields" : { 
                            "status": { "name": "Done" }
                        } 
                    }"""
                } else if (script.contains('gh pr create')) {
                    return "389"
                } else if (script.contains('gh pr view')) {
                    return """{ "state": "CLOSED" }"""
                } else if (script.contains('gh api /repos/{owner}/{repo}/commits')) {
                    return """{ "sha": "$DEFAULT_GIT_COMMIT_SHA" }"""
                } else if (script.contains('curl -I -s -o /dev/null -w \"%{http_code}\" -L')) {
                    return "200"
                } else if (script.contains('lcaexec issue google')) {
                    return "lca_test_token_abcdefgh"
                } else if (script.contains('conversations.create')) {
                    return """
                        {"ok":true,"channel":{"id":"C050HUQ9XV0","name":"camkit-4226-release-1-22-0"}}
                    """
                } else {
                    helper.runSh(args)
                }
            } else {
                helper.runSh(args)
            }
        })

        helper.addReadFileMock(
                "snap-sdk-android-publish/$DEFAULT_BUILD_JOB_NUMBER/publications.txt",
                'com.snap.camerakit:camerakit:1.1.0'
        )
        helper.addReadFileMock('VERSION', '1.0.0')
        helper.addReadFileMock('CHANGELOG.md', new File('../../CHANGELOG.md').text)

        Map<String, File> temporaryFiles = [:]
        helper.registerAllowedMethod('fileExists', [String], { String arg ->
            if (temporaryFiles[arg] != null) {
                true
            } else {
                helper.fileExists(arg)
            }
        })
        helper.registerAllowedMethod('readFile', [String], { String arg ->
            def file = temporaryFiles[arg]
            if (file == null) {
                helper.readFile(arg)
            } else {
                file.text
            }
        })
        helper.registerAllowedMethod('writeFile', [Map], { args ->
            def filePath = args['file']
            def text = args['text']
            def file = temporaryFiles[filePath]
            if (file == null) {
                file = temporaryFolder.newFile(filePath)
                temporaryFiles[filePath] = file
            }
            file.write(text)
            null
        })
    }

    @Test
    void sanity() {
        runScript("src/main/groovy/com/snap/camerakit/jenkins/pipeline/camerakit-release.groovy")

        assertJobStatusSuccess()
        printCallStack()
    }
}
