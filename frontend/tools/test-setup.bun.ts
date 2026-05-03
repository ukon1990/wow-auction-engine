const delegateEnvKey = 'ETHEREAL_BUN_TEST_DELEGATED';

if (Bun.env[delegateEnvKey] !== '1') {
  const result = Bun.spawnSync(['bun', 'run', 'test:ci'], {
    stdout: 'inherit',
    stderr: 'inherit',
    env: {
      ...Bun.env,
      [delegateEnvKey]: '1',
    },
  });

  process.exit(result.exitCode ?? 1);
}
