import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import {
  ProfessionAllocation,
  ProfessionProfile,
  ProfessionProfileRequest,
  ProfessionSkillTree,
  ProfileApiService,
  ProfileCharacter,
  ProfileCharacterProfession,
  ProfileCharacterProfessionSource,
  ProfileCharacterRequest,
  CharacterProfessionPreview,
} from '@api/generated';
import { PageFrameComponent, SelectInputComponent, TextInputComponent } from '@ui';
import { UserRole } from '@api/auth/auth.model';
import { AuthService } from '@core/services/auth.service';
import { CharacterProfessionPreviewStorageService } from './character-profession-preview-storage.service';
import {
  ProfessionSkillTreeEditor,
  SkillTreeGraphNode,
} from './profession-skill-tree-editor.component';

const midnightExpansionId = 12;
const professions = [
  { id: 164, label: $localize`:@@professionProfiles.profession.blacksmithing:Blacksmithing` },
  { id: 165, label: $localize`:@@professionProfiles.profession.leatherworking:Leatherworking` },
  { id: 171, label: $localize`:@@professionProfiles.profession.alchemy:Alchemy` },
  { id: 182, label: $localize`:@@professionProfiles.profession.herbalism:Herbalism` },
  { id: 186, label: $localize`:@@professionProfiles.profession.mining:Mining` },
  { id: 197, label: $localize`:@@professionProfiles.profession.tailoring:Tailoring` },
  { id: 202, label: $localize`:@@professionProfiles.profession.engineering:Engineering` },
  { id: 333, label: $localize`:@@professionProfiles.profession.enchanting:Enchanting` },
  { id: 393, label: $localize`:@@professionProfiles.profession.skinning:Skinning` },
  { id: 755, label: $localize`:@@professionProfiles.profession.jewelcrafting:Jewelcrafting` },
  { id: 773, label: $localize`:@@professionProfiles.profession.inscription:Inscription` },
] as const;

@Component({
  selector: 'app-profession-profiles-page',
  imports: [
    FormsModule,
    RouterLink,
    PageFrameComponent,
    SelectInputComponent,
    TextInputComponent,
    ProfessionSkillTreeEditor,
  ],
  templateUrl: './profession-profiles.page.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfessionProfilesPage {
  private readonly profileApi = inject(ProfileApiService);
  private readonly previewStorage = inject(CharacterProfessionPreviewStorageService);
  private readonly auth = inject(AuthService);

  protected readonly characters = signal<readonly ProfileCharacter[]>([]);
  protected readonly selectedCharacterId = signal<number | null>(null);
  protected readonly selectedProfessionId = signal<number | null>(null);
  protected readonly knownProfessions = signal<readonly ProfileCharacterProfession[]>([]);
  protected readonly trees = signal<readonly ProfessionSkillTree[]>([]);
  protected readonly selectedTreeId = signal<number | null>(null);
  protected readonly allocations = signal<ReadonlyMap<number, number>>(new Map());
  protected readonly loading = signal(true);
  protected readonly loadingTree = signal(false);
  protected readonly saving = signal(false);
  protected readonly addingCharacter = signal(false);
  protected readonly lookingUpCharacter = signal(false);
  protected readonly syncingCharacter = signal(false);
  protected readonly showNewCharacterForm = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly status = signal<string | null>(null);
  protected readonly characterRegion = signal('eu');
  protected readonly characterRealmSlug = signal('');
  protected readonly characterName = signal('');
  protected readonly preview = signal<CharacterProfessionPreview | null>(null);
  private readonly savedAllocations = signal<ReadonlyMap<number, number>>(new Map());
  private readonly savedTreeId = signal<number | null>(null);

  protected readonly characterOptions = computed(() =>
    this.characters().map((character) => ({
      id: character.id.toString(),
      label: `${character.characterName} — ${character.realmName} (${character.region.toUpperCase()})`,
    })),
  );
  protected readonly professionOptions = computed(() =>
    this.knownProfessions().map((profession) => ({
      id: String(profession.professionId),
      label: professionLabel(profession.professionId),
    })),
  );
  protected readonly selectedCharacter = computed(
    () =>
      this.characters().find((character) => character.id === this.selectedCharacterId()) ?? null,
  );
  protected readonly selectedTree = computed(
    () => this.trees().find((tree) => tree.id === this.selectedTreeId()) ?? null,
  );
  protected readonly isAdmin = computed(
    () => this.auth.user()?.roles.includes(UserRole.Admin) ?? false,
  );
  protected readonly hasUnsavedChanges = computed(
    () =>
      this.selectedTreeId() !== this.savedTreeId() ||
      !sameAllocations(this.allocations(), this.savedAllocations()),
  );
  protected readonly hasKnownProfessions = computed(() => this.knownProfessions().length > 0);
  protected readonly professionLabel = professionLabel;

  constructor() {
    void this.loadCharacters();
  }

  protected async selectCharacter(value: string): Promise<void> {
    if (this.hasUnsavedChanges() && !this.confirmDiscardChanges()) return;
    this.showNewCharacterForm.set(false);
    this.preview.set(null);
    this.selectedCharacterId.set(value ? Number(value) : null);
    await this.loadProfileAndTrees();
  }

  protected async selectProfession(value: string): Promise<void> {
    if (this.hasUnsavedChanges() && !this.confirmDiscardChanges()) return;
    this.selectedProfessionId.set(Number(value));
    await this.loadProfileAndTrees();
  }

  protected openNewCharacterForm(): void {
    this.showNewCharacterForm.set(true);
    this.preview.set(null);
    this.error.set(null);
    this.status.set(null);
  }

  protected closeNewCharacterForm(): void {
    this.showNewCharacterForm.set(false);
    this.preview.set(null);
  }

  protected async syncSelectedCharacter(): Promise<void> {
    const character = this.selectedCharacter();
    if (!character) return;

    this.syncingCharacter.set(true);
    this.error.set(null);
    this.status.set(null);
    try {
      const professions = await firstValueFrom(
        this.profileApi.syncProfileCharacterBlizzard(character.id),
      );
      this.knownProfessions.set(professions);
      if (
        professions.length &&
        !professions.some((profession) => profession.professionId === this.selectedProfessionId())
      ) {
        this.selectedProfessionId.set(professions[0].professionId);
      }
      await this.loadProfileAndTrees();
      this.status.set(
        $localize`:@@professionProfiles.status.characterSynced:${professions.length}:INTERPOLATION: professions imported from Blizzard.`,
      );
    } catch {
      this.error.set(
        $localize`:@@professionProfiles.error.syncCharacter:Unable to import professions from Blizzard for this character.`,
      );
    } finally {
      this.syncingCharacter.set(false);
    }
  }

  protected async addCharacter(): Promise<void> {
    const preview = this.preview();
    if (!preview) {
      this.error.set(
        $localize`:@@professionProfiles.error.lookupRequired:Look up the character with Blizzard first.`,
      );
      return;
    }

    this.addingCharacter.set(true);
    this.error.set(null);
    try {
      const request: ProfileCharacterRequest = {
        region: preview.region,
        realmName: preview.realmSlug,
        characterName: preview.characterName,
      };
      const character = await firstValueFrom(this.profileApi.createProfileCharacter(request));
      this.characters.update((characters) => {
        const withoutExisting = characters.filter((candidate) => candidate.id !== character.id);
        return [...withoutExisting, character];
      });
      this.selectedCharacterId.set(character.id);
      this.preview.set(null);
      this.showNewCharacterForm.set(false);
      const professions = await firstValueFrom(
        this.profileApi.syncProfileCharacterBlizzard(character.id),
      );
      this.knownProfessions.set(professions);
      if (professions.length) {
        this.selectedProfessionId.set(professions[0].professionId);
      }
      this.status.set($localize`:@@professionProfiles.status.characterAdded:Character added.`);
      await this.loadProfileAndTrees();
    } catch {
      this.error.set(
        $localize`:@@professionProfiles.error.addCharacter:Unable to add this character. Check the details and try again.`,
      );
    } finally {
      this.addingCharacter.set(false);
    }
  }

  protected async lookupCharacter(): Promise<void> {
    const region = this.characterRegion().trim().toLowerCase();
    const realmSlug = this.characterRealmSlug().trim().toLowerCase();
    const characterName = this.characterName().trim();
    if (!isBlizzardRegion(region) || !realmSlug || !characterName) {
      this.error.set(
        $localize`:@@professionProfiles.error.characterRequired:Enter a region, realm, and character name.`,
      );
      return;
    }

    this.lookingUpCharacter.set(true);
    this.error.set(null);
    this.status.set(null);
    try {
      const preview = await firstValueFrom(
        this.profileApi.getCharacterProfessionPreview(region, realmSlug, characterName),
      );
      this.preview.set(preview);
      this.previewStorage.save(preview);
      this.status.set(
        $localize`:@@professionProfiles.status.characterFound:Character profession data loaded. Review it, then add the character.`,
      );
    } catch {
      const cached = this.previewStorage.get(region, realmSlug, characterName);
      if (cached) {
        this.preview.set(cached);
        this.status.set(
          $localize`:@@professionProfiles.status.cachedCharacter:Blizzard is unavailable, so the last saved profession preview is shown.`,
        );
      } else {
        this.preview.set(null);
        this.error.set(
          $localize`:@@professionProfiles.error.lookupCharacter:Unable to load professions from Blizzard. Check the realm slug and character name, then try again.`,
        );
      }
    } finally {
      this.lookingUpCharacter.set(false);
    }
  }

  protected clearPreview(): void {
    this.preview.set(null);
  }

  protected async addCharacterManually(): Promise<void> {
    const region = this.characterRegion().trim().toLowerCase();
    const realmName = this.characterRealmSlug().trim();
    const characterName = this.characterName().trim();
    if (!region || !realmName || !characterName) {
      this.error.set(
        $localize`:@@professionProfiles.error.characterRequired:Enter a region, realm, and character name.`,
      );
      return;
    }

    this.addingCharacter.set(true);
    this.error.set(null);
    try {
      const character = await firstValueFrom(
        this.profileApi.createProfileCharacter({ region, realmName, characterName }),
      );
      this.characters.update((characters) => [...characters, character]);
      this.selectedCharacterId.set(character.id);
      this.preview.set(null);
      this.showNewCharacterForm.set(false);
      this.knownProfessions.set([]);
      this.status.set($localize`:@@professionProfiles.status.characterAdded:Character added.`);
      await this.loadProfileAndTrees();
    } catch {
      this.error.set(
        $localize`:@@professionProfiles.error.addCharacter:Unable to add this character. Check the details and try again.`,
      );
    } finally {
      this.addingCharacter.set(false);
    }
  }

  protected knownRecipeCount(preview: CharacterProfessionPreview): number {
    return preview.professions.reduce(
      (total, profession) =>
        total +
        profession.tiers.reduce((tierTotal, tier) => tierTotal + tier.knownRecipes.length, 0),
      0,
    );
  }

  protected knownRecipeCountForProfession(
    profession: CharacterProfessionPreview['professions'][number],
  ): number {
    return profession.tiers.reduce((total, tier) => total + tier.knownRecipes.length, 0);
  }

  protected sourceSummary(profession: ProfileCharacterProfession): string {
    return profession.sources.map((source) => sourceLabel(source)).join(', ');
  }

  protected rankFor(entryId: number): number {
    return this.allocations().get(entryId) ?? 0;
  }

  protected selectedCharacterOption(): string {
    return this.selectedCharacterId()?.toString() ?? '';
  }

  protected selectedProfessionOption(): string {
    return this.selectedProfessionId()?.toString() ?? '';
  }

  protected treeOptions(): readonly { id: string; label: string }[] {
    return this.trees().map((tree) => ({ id: String(tree.id), label: tree.name }));
  }

  protected async selectTree(value: string): Promise<void> {
    const treeId = Number(value);
    if (treeId === this.selectedTreeId()) return;
    if (this.hasUnsavedChanges() && !this.confirmDiscardChanges()) return;
    this.selectedTreeId.set(treeId);
    this.allocations.set(new Map());
    this.status.set(null);
  }

  protected updateRank(node: SkillTreeGraphNode, entryId: number, change: number): void {
    const entry = node.entries.find((candidate) => candidate.id === entryId);
    if (!entry) return;
    const currentRank = this.rankFor(entryId);
    const nodeRank = node.entries.reduce(
      (total, candidate) => total + this.rankFor(candidate.id),
      0,
    );
    const availableNodeRanks = Math.max(0, node.maxRanks - (nodeRank - currentRank));
    const nextRank = Math.max(
      0,
      Math.min(entry.rankLimit, availableNodeRanks, currentRank + change),
    );
    this.allocations.update((current) => {
      const next = new Map(current);
      if (nextRank === 0) next.delete(entryId);
      else next.set(entryId, nextRank);
      return next;
    });
    this.status.set(null);
  }

  protected async save(): Promise<void> {
    const character = this.selectedCharacter();
    const tree = this.selectedTree();
    const professionId = this.selectedProfessionId();
    if (!character || !tree || professionId == null) return;

    this.saving.set(true);
    this.error.set(null);
    try {
      const allocations: ProfessionAllocation[] = [...this.allocations()].map(
        ([entryId, rank]) => ({
          entryId,
          rank,
        }),
      );
      const request: ProfessionProfileRequest = { treeId: tree.id, allocations };
      const profile = await firstValueFrom(
        this.profileApi.putProfessionProfile(character.id, professionId, request),
      );
      this.applyProfile(profile, tree.id);
      await this.loadKnownProfessions(character.id);
      this.status.set($localize`:@@professionProfiles.status.saved:Skill-tree profile saved.`);
    } catch {
      this.error.set(
        $localize`:@@professionProfiles.error.save:Unable to save your skill-tree profile. Your edits are still here.`,
      );
    } finally {
      this.saving.set(false);
    }
  }

  confirmDiscardChanges(): boolean {
    if (!this.hasUnsavedChanges()) return true;
    return typeof window === 'undefined'
      ? true
      : window.confirm(
          $localize`:@@professionProfiles.confirmDiscard:Discard unsaved skill-tree changes?`,
        );
  }

  private async loadCharacters(): Promise<void> {
    try {
      const characters = await firstValueFrom(this.profileApi.listProfileCharacters());
      this.characters.set(characters);
      this.selectedCharacterId.set(characters[0]?.id ?? null);
      await this.loadProfileAndTrees();
    } catch {
      this.error.set(
        $localize`:@@professionProfiles.error.loadCharacters:Unable to load your characters.`,
      );
    } finally {
      this.loading.set(false);
    }
  }

  private async loadProfileAndTrees(): Promise<void> {
    const characterId = this.selectedCharacterId();
    if (!characterId) {
      this.resetProfile();
      return;
    }

    this.loadingTree.set(true);
    this.error.set(null);
    try {
      await this.loadKnownProfessions(characterId);
      const professionId = this.selectedProfessionId();
      if (professionId == null) {
        this.resetProfile();
        return;
      }

      const [trees, profile] = await Promise.all([
        firstValueFrom(
          this.profileApi.listProfessionTrees(midnightExpansionId, professionId),
        ),
        firstValueFrom(this.profileApi.getProfessionProfile(characterId, professionId)),
      ]);
      this.trees.set(trees);
      const treeId = preferredTreeId(trees);
      this.selectedTreeId.set(treeId);
      if (profile.treeId === treeId) {
        this.applyProfile(profile, treeId);
      } else {
        this.allocations.set(new Map());
        this.savedAllocations.set(new Map());
        this.savedTreeId.set(profile.treeId ?? null);
      }
    } catch {
      this.resetProfile();
      this.error.set(
        $localize`:@@professionProfiles.error.loadProfile:Unable to load this profession profile.`,
      );
    } finally {
      this.loadingTree.set(false);
    }
  }

  private async loadKnownProfessions(characterId: number): Promise<void> {
    const known = await firstValueFrom(
      this.profileApi.listProfileCharacterProfessions(characterId),
    );
    this.knownProfessions.set(known);
    if (!known.length) {
      this.selectedProfessionId.set(null);
      return;
    }
    if (!known.some((profession) => profession.professionId === this.selectedProfessionId())) {
      this.selectedProfessionId.set(known[0].professionId);
    }
  }

  private applyProfile(profile: ProfessionProfile, treeId: number | null): void {
    const allocations = new Map(
      profile.allocations.map((allocation) => [allocation.entryId, allocation.rank]),
    );
    this.allocations.set(allocations);
    this.savedAllocations.set(new Map(allocations));
    this.selectedTreeId.set(treeId);
    this.savedTreeId.set(treeId);
  }

  private resetProfile(): void {
    this.knownProfessions.set([]);
    this.selectedProfessionId.set(null);
    this.trees.set([]);
    this.selectedTreeId.set(null);
    this.allocations.set(new Map());
    this.savedAllocations.set(new Map());
    this.savedTreeId.set(null);
  }
}

export function professionLabel(professionId: number): string {
  return professions.find((profession) => profession.id === professionId)?.label ?? `#${professionId}`;
}

function sourceLabel(source: ProfileCharacterProfessionSource): string {
  switch (source) {
    case ProfileCharacterProfessionSource.Blizzard:
      return $localize`:@@professionProfiles.source.blizzard:Blizzard`;
    case ProfileCharacterProfessionSource.Addon:
      return $localize`:@@professionProfiles.source.addon:Addon`;
    case ProfileCharacterProfessionSource.Manual:
      return $localize`:@@professionProfiles.source.manual:Manual`;
  }
}

function sameAllocations(
  left: ReadonlyMap<number, number>,
  right: ReadonlyMap<number, number>,
): boolean {
  if (left.size !== right.size) return false;
  return [...left].every(([entryId, rank]) => right.get(entryId) === rank);
}

function isBlizzardRegion(region: string): region is CharacterProfessionPreview['region'] {
  return region === 'us' || region === 'eu' || region === 'kr' || region === 'tw';
}

export function preferredTreeId(
  trees: readonly Pick<ProfessionSkillTree, 'id' | 'externalTreeId'>[],
): number | null {
  return (
    trees.reduce<(typeof trees)[number] | null>(
      (highest, tree) =>
        highest === null || tree.externalTreeId > highest.externalTreeId ? tree : highest,
      null,
    )?.id ?? null
  );
}
