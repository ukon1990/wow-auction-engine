import '@angular/compiler';
import '@angular/localize/init';
import '@analogjs/vitest-angular/setup-snapshots';
import '@analogjs/vitest-angular/setup-serializers';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ensureWebStorage } from './vitest.storage-setup';

ensureWebStorage();
setupTestBed();
