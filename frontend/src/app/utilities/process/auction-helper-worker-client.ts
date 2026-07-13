import { AuctionHelperLocalPreview } from './auction-helper-file-processor';

type WorkerResponse =
  { ok: true; preview: AuctionHelperLocalPreview } | { ok: false; error: string };

export function processAuctionHelperFilesInWorker(
  files: readonly File[],
  region: string,
): Promise<AuctionHelperLocalPreview> {
  const worker = new Worker(new URL('./auction-helper-processing.worker', import.meta.url), {
    type: 'module',
  });
  return new Promise((resolve, reject) => {
    worker.onmessage = ({ data }: MessageEvent<WorkerResponse>) => {
      worker.terminate();
      if (data.ok) resolve(data.preview);
      else reject(new Error(data.error));
    };
    worker.onerror = (event) => {
      worker.terminate();
      reject(new Error(event.message || 'Local processing worker failed.'));
    };
    worker.postMessage({ files: [...files], region });
  });
}
