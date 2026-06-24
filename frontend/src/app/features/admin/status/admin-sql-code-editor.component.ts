import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { indentWithTab } from '@codemirror/commands';
import { MySQL, sql } from '@codemirror/lang-sql';
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { tags } from '@lezer/highlight';
import { EditorState } from '@codemirror/state';
import { EditorView, keymap, placeholder as cmPlaceholder } from '@codemirror/view';
import { format as formatSql } from 'sql-formatter';

const adminSqlHighlightStyle = HighlightStyle.define([
  { tag: tags.keyword, color: '#ecb913' },
  { tag: tags.controlKeyword, color: '#ecb913' },
  { tag: tags.operatorKeyword, color: '#ecb913' },
  { tag: tags.definitionKeyword, color: '#ffd773' },
  { tag: tags.modifier, color: '#ffd773' },
  { tag: tags.string, color: '#63cbff' },
  { tag: tags.special(tags.string), color: '#63cbff' },
  { tag: tags.number, color: '#ebe1d1' },
  { tag: tags.bool, color: '#ffd773' },
  { tag: tags.null, color: '#ffd773' },
  { tag: tags.comment, color: '#9b9079', fontStyle: 'italic' },
  { tag: tags.lineComment, color: '#9b9079', fontStyle: 'italic' },
  { tag: tags.blockComment, color: '#9b9079', fontStyle: 'italic' },
  { tag: tags.operator, color: '#d2c5ac' },
  { tag: tags.punctuation, color: '#d2c5ac' },
  { tag: tags.bracket, color: '#d2c5ac' },
  { tag: tags.variableName, color: '#ebe1d1' },
  { tag: tags.name, color: '#ebe1d1' },
  { tag: tags.typeName, color: '#b3e2ff' },
  { tag: tags.className, color: '#b3e2ff' },
  { tag: tags.propertyName, color: '#ebe1d1' },
  { tag: tags.function(tags.variableName), color: '#ebe1d1' },
]);

@Component({
  selector: 'app-admin-sql-code-editor',
  template: `
    <div
      #host
      class="overflow-hidden rounded-md border border-white/10 bg-surface-container font-mono text-sm text-on-surface outline-none transition focus-within:border-primary-container focus-within:ring-2 focus-within:ring-primary-container/40"
      [style.min-height]="minHeight()"
      [style.max-height]="maxHeight() || null"
    ></div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminSqlCodeEditorComponent {
  readonly value = input('');
  readonly readOnly = input(false);
  readonly placeholder = input('SELECT * FROM item');
  readonly minHeight = input('14rem');
  readonly maxHeight = input('');
  readonly enterToSubmit = input(false);

  readonly valueChange = output<string>();
  readonly enterPressed = output<void>();

  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('host');
  private readonly destroyRef = inject(DestroyRef);

  private view: EditorView | null = null;
  private syncingExternalValue = false;

  constructor() {
    afterNextRender(() => {
      this.initEditor();
      this.destroyRef.onDestroy(() => this.view?.destroy());
    });

    effect(() => {
      const next = this.value();
      if (!this.view || this.view.state.doc.toString() === next) {
        return;
      }
      this.setDocument(next);
    });
  }

  format(): void {
    const current = this.view?.state.doc.toString().trim() ?? '';
    if (!current) {
      return;
    }
    try {
      const formatted = formatSql(current, { language: 'mariadb' });
      this.setDocument(formatted);
      this.valueChange.emit(formatted);
    } catch {
      // Keep unformatted SQL when the formatter cannot parse the statement.
    }
  }

  focus(): void {
    this.view?.focus();
  }

  private initEditor(): void {
    const host = this.host().nativeElement;
    const maxHeight = this.maxHeight();
    const extensions = [
      sql({ dialect: MySQL }),
      syntaxHighlighting(adminSqlHighlightStyle, { fallback: true }),
      keymap.of([
        indentWithTab,
        {
          key: 'Enter',
          run: () => {
            if (this.enterToSubmit()) {
              this.enterPressed.emit();
              return true;
            }
            return false;
          },
        },
      ]),
      EditorView.lineWrapping,
      cmPlaceholder(this.placeholder()),
      EditorView.theme({
        '&': {
          backgroundColor: 'transparent',
          maxHeight: maxHeight || 'none',
        },
        '.cm-editor': {
          maxHeight: maxHeight || 'none',
        },
        '.cm-scroller': {
          fontFamily: 'inherit',
          lineHeight: '1.5',
          overflow: 'auto',
          maxHeight: maxHeight || 'none',
        },
        '.cm-content': { padding: '0.75rem', caretColor: 'rgb(236 185 19)' },
        '.cm-gutters': { display: 'none' },
        '&.cm-focused': { outline: 'none' },
      }),
      EditorView.updateListener.of((update) => {
        if (!update.docChanged || this.syncingExternalValue) {
          return;
        }
        this.valueChange.emit(update.state.doc.toString());
      }),
      EditorState.readOnly.of(this.readOnly()),
    ];

    this.view = new EditorView({
      parent: host,
      state: EditorState.create({
        doc: this.value(),
        extensions,
      }),
    });
  }

  private setDocument(value: string): void {
    if (!this.view) {
      return;
    }
    this.syncingExternalValue = true;
    this.view.dispatch({
      changes: { from: 0, to: this.view.state.doc.length, insert: value },
    });
    this.syncingExternalValue = false;
  }
}
