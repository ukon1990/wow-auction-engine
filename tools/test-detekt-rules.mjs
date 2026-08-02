#!/usr/bin/env node

import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const backendRoot = path.join(repositoryRoot, "backend");

function sequentialConditions(conditionCount, functionName) {
    const conditions = Array.from(
        { length: conditionCount },
        (_, index) => `    if (inputValue == ${index}) resultValue += 1`,
    ).join("\n");
    return `fun ${functionName}(inputValue: Int): Int {
    var resultValue = 0
${conditions}
    return resultValue
}`;
}

function nestedConditions(nestingDepth, functionName, addSequentialCondition = false) {
    const openingConditions = Array.from(
        { length: nestingDepth },
        (_, index) => `${"    ".repeat(index + 1)}if (inputValue > ${index}) {`,
    );
    const closingConditions = Array.from(
        { length: nestingDepth },
        (_, index) => `${"    ".repeat(nestingDepth - index)}}`,
    );
    const sequentialCondition = addSequentialCondition ? ["    if (inputValue < 0) return inputValue"] : [];
    return [
        `fun ${functionName}(inputValue: Int): Int {`,
        ...openingConditions,
        `${"    ".repeat(nestingDepth + 1)}println(inputValue)`,
        ...closingConditions,
        ...sequentialCondition,
        "    return inputValue",
        "}",
    ].join("\n");
}

function classWithLineCount(className, lineCount) {
    const propertyCount = lineCount - 2;
    const properties = Array.from(
        { length: propertyCount },
        (_, index) => `    val meaningfulProperty${index} = ${index}`,
    );
    return [`class ${className} {`, ...properties, "}"].join("\n");
}

async function writeFixture(root, relativePath, contents) {
    const filePath = path.join(root, relativePath);
    await mkdir(path.dirname(filePath), { recursive: true });
    await writeFile(filePath, `${contents}\n`, "utf8");
}

function runDetekt(inputPath) {
    return spawnSync(
        path.join(backendRoot, "mvnw"),
        ["-B", "-ntp", `-Ddetekt.input=${inputPath}`, "antrun:run@detekt"],
        { cwd: backendRoot, encoding: "utf8" },
    );
}

function combinedOutput(result) {
    return `${result.stdout ?? ""}\n${result.stderr ?? ""}`;
}

async function assertConfiguredScope() {
    const pom = await readFile(path.join(backendRoot, "pom.xml"), "utf8");
    const inputProperty =
        "<detekt.input>${project.basedir}/src/main/kotlin:${project.basedir}/src/test/kotlin</detekt.input>";
    assert.ok(pom.includes(inputProperty), "Detekt input must contain only handwritten source roots");
    assert.equal(inputProperty.includes("target/generated-sources"), false);
}

async function run() {
    await assertConfiguredScope();
    const fixtureRoot = await mkdtemp(path.join(os.tmpdir(), "wow-detekt-rules-"));

    try {
        const passingRoot = path.join(fixtureRoot, "passing");
        await writeFixture(
            passingRoot,
            "ComplexityAtLimit.kt",
            [
                sequentialConditions(14, "cyclomaticAtLimit"),
                nestedConditions(5, "cognitiveAtLimit"),
                "fun underscoreLambdaIsAllowed() = listOf(1).map { _ -> 1 }",
            ].join("\n\n"),
        );
        await writeFixture(passingRoot, "ClassAtLimit.kt", classWithLineCount("ClassAtLimit", 600));
        const passingResult = runDetekt(passingRoot);
        assert.equal(passingResult.status, 0, combinedOutput(passingResult));

        const failingRoot = path.join(fixtureRoot, "failing");
        await writeFixture(
            failingRoot,
            "ComplexityOverLimit.kt",
            [sequentialConditions(15, "cyclomaticOverLimit"), nestedConditions(5, "cognitiveOverLimit", true)].join(
                "\n\n",
            ),
        );
        await writeFixture(failingRoot, "ClassOverLimit.kt", classWithLineCount("ClassOverLimit", 601));
        await writeFixture(
            failingRoot,
            "NamingOverLimit.kt",
            [
                "fun shortFunctionParameter(x: Int) = x",
                "fun shortVariable() { val y = 1; println(y) }",
                "fun shortLambda() = listOf(1).map { z -> z }",
            ].join("\n"),
        );
        const failingResult = runDetekt(failingRoot);
        const failingOutput = combinedOutput(failingResult);
        assert.notEqual(failingResult.status, 0, "Over-limit Detekt fixtures unexpectedly passed");
        assert.match(failingOutput, /complexity: 16.*\[CyclomaticComplexMethod\]/);
        assert.match(failingOutput, /complexity: 16.*\[CognitiveComplexMethod\]/);
        assert.match(failingOutput, /\[LargeClass\]/);
        assert.match(failingOutput, /\[FunctionParameterNaming\]/);
        assert.match(failingOutput, /\[VariableNaming\]/);
        assert.match(failingOutput, /\[LambdaParameterNaming\]/);

        const excludedTestRoot = path.join(fixtureRoot, "project/src/test/kotlin");
        await writeFixture(excludedTestRoot, "LargeTestFixture.kt", classWithLineCount("LargeTestFixture", 601));
        const excludedTestResult = runDetekt(path.join(fixtureRoot, "project/src/test/kotlin"));
        assert.equal(excludedTestResult.status, 0, combinedOutput(excludedTestResult));
    } finally {
        await rm(fixtureRoot, { recursive: true, force: true });
    }

    console.log("Detekt complexity, class-size, naming, and scope self-tests passed.");
}

await run();
