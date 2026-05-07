import { registerLocaleData } from '@angular/common';
import localeDe from '@angular/common/locales/de';
import localeEs from '@angular/common/locales/es';
import localeFr from '@angular/common/locales/fr';
import localeIt from '@angular/common/locales/it';
import localeKo from '@angular/common/locales/ko';
import localePt from '@angular/common/locales/pt';
import localeRu from '@angular/common/locales/ru';
import localeZh from '@angular/common/locales/zh';

export function registerAppLocaleData(): void {
  registerLocaleData(localeDe, 'de');
  registerLocaleData(localeEs, 'es');
  registerLocaleData(localeFr, 'fr');
  registerLocaleData(localeIt, 'it');
  registerLocaleData(localeKo, 'ko');
  registerLocaleData(localePt, 'pt');
  registerLocaleData(localeRu, 'ru');
  registerLocaleData(localeZh, 'zh');
}
