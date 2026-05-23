import { registerLocaleData } from '@angular/common';
import localeDe from '@angular/common/locales/de';
import localeEs from '@angular/common/locales/es';
import localeEsMx from '@angular/common/locales/es-MX';
import localeFr from '@angular/common/locales/fr';
import localeIt from '@angular/common/locales/it';
import localeKo from '@angular/common/locales/ko';
import localePt from '@angular/common/locales/pt';
import localePtPt from '@angular/common/locales/pt-PT';
import localeRu from '@angular/common/locales/ru';
import localeZh from '@angular/common/locales/zh';
import localeZhHant from '@angular/common/locales/zh-Hant';
import localeEnGb from '@angular/common/locales/en-GB';

export function registerAppLocaleData(): void {
  registerLocaleData(localeEnGb, 'en-GB');
  registerLocaleData(localeDe, 'de');
  registerLocaleData(localeEs, 'es');
  registerLocaleData(localeEsMx, 'es-MX');
  registerLocaleData(localeFr, 'fr');
  registerLocaleData(localeIt, 'it');
  registerLocaleData(localeKo, 'ko');
  registerLocaleData(localePt, 'pt');
  registerLocaleData(localePtPt, 'pt-PT');
  registerLocaleData(localeRu, 'ru');
  registerLocaleData(localeZh, 'zh');
  registerLocaleData(localeZhHant, 'zh-TW');
}
