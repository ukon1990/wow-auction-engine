/// <reference lib="webworker" />
import { processAuctionHelperFiles } from './auction-helper-file-processor';

addEventListener('message', async ({ data }: MessageEvent<{ files: File[]; region: string }>) => {
  try {
    postMessage({ ok: true, preview: await processAuctionHelperFiles(data.files, data.region) });
  } catch (cause) {
    postMessage({
      ok: false,
      error: cause instanceof Error ? cause.message : 'Unable to process SavedVariables.',
    });
  }
});
