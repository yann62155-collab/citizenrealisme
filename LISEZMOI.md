# Citizen Réalisme

Mod NeoForge **21.1.215** (Minecraft 1.21.1, Java 21) pour le serveur RP Citizen.
Il rend l'inventaire, le craft et l'enchantement plus réalistes. Toutes les règles sont gérées
par le serveur : elles s'appliquent de la même façon aux joueurs PC et aux joueurs console (Citizen Bedrock).

## Installation
- **Serveur** : mettre `citizenrealisme-x.x.x.jar` dans `mods`.
- **Joueurs PC** : l'ajouter à Citizen Updater (HUD du poids, barre de fabrication, taux du rituel, interface parchemin).
- **Joueurs console** : rien à installer. Les infos s'affichent en barre d'action, et `/realisme` ouvre un menu console.

## Ce que ça change
### Inventaire
- **Poids** : chaque objet a un poids (affiché dans l'infobulle sur PC). Capacité de base 60 kg.
  Au-delà de 100 % : ralentissement. À partir de 125 % : plus de sprint. À partir de 150 % : plus de saut.
- **Cases limitées** : sans sac, seules la barre rapide et la 1re rangée sont ouvertes. Les cases fermées affichent une barrière « Case verrouillée ».
  Les objets déjà rangés dans une case fermée ne sont jamais supprimés : on peut toujours les sortir.
- **Sacs** (clic droit pour équiper, `/realisme sac retirer` pour enlever, il doit être vide) :
  - Sacoche : +9 cases, +20 kg. Recette : `cuir ficelle cuir / cuir cuir cuir` (+ aiguille).
  - Sac à dos : +18 cases, +45 kg. Recette : `ficelle cuir ficelle / cuir coffre cuir / cuir cuir cuir` (+ aiguille).
  - À la mort, le sac tombe au sol avec le reste (sauf keepInventory).
- **Équipement visible** : sac dans le dos, arc/arbalète/trident/masse en bandoulière, épée et outil à la ceinture
  (objets de la barre rapide qui ne sont pas en main).

### Sophisticated Backpacks (compatibilité)
- Le contenu des sacs Sophisticated Backpacks compte dans le poids, qu'ils soient dans l'inventaire
  ou portés dans le dos (emplacements Curios / Accessories). Un sac vide pèse 1,5 kg.
- La sacoche et le sac à dos Citizen restent nécessaires pour ouvrir les rangées de l'inventaire.
- Réglages : section `sophisticatedBackpacks` de `realisme.json` (`compterContenu`, `poidsSacVide`,
  `bonusCapaciteSiPorte` pour donner de la capacité quand un sac est porté, 0 par défaut).

### Craft
- Crafts simples (2 cases ou moins, sans outil) : instantanés.
- Les autres prennent du temps : clic sur le résultat → barre de progression → l'objet arrive dans l'inventaire. Bouger annule.
- **Taux de réussite** affiché avant de lancer. Échec = une partie des ingrédients est perdue.
- **Outils d'artisan** (à garder dans l'inventaire, ils s'usent ; en bon état : +5 % de réussite) :
  - Marteau (`II / IS / _S`, fer + bâtons) : armes, armures, objets en métal.
  - Scie (`III / SS_`, demande un marteau) : portes, trappes, barrières, coffres, tonneaux, bateaux, panneaux, échelles…
  - Aiguille (`pépite / ficelle`) : armures en cuir, lits, bannières, tapis, livres, laisses, sacs.

### Enchantement (rituel)
- Chaque option de la table affiche un **taux de réussite** (+1 % par bibliothèque, 15 max).
- Elle demande des **catalyseurs** en plus du lapis et des niveaux :
  option 1 : 1 éclat d'améthyste · option 2 : 2 améthystes + 1 poudre de glowstone · option 3 : 3 améthystes + 1 perle de l'Ender.
- La chance de rater = 100 % − taux affiché. Échec : niveaux, lapis et catalyseurs perdus ; 35 % de chance d'une malédiction.

### Interface parchemin (PC)
Les écrans inventaire, établi et table d'enchantement sont recolorés en parchemin à partir des textures du jeu.

## Commandes
- `/realisme` : poids, sac, cases (menu natif sur console).
- `/realisme sac retirer`, `/realisme craft`, `/realisme rituel`.
- `/realisme admin recharger` (op) : relit `config/citizenrealisme/realisme.json`.
- `/realisme admin donner <marteau|scie|aiguille|sacoche|sacados> [joueur]` (op).

## Réglages
Tout se règle dans `config/citizenrealisme/realisme.json` (poids par objet ou par tag, capacités,
rangées ouvertes, durées, taux, outils par objet, catalyseurs, équipement visible), puis `/realisme admin recharger`.

## Limites connues
- Sur console, le saut n'est pas bloqué en surcharge (Geyser ne transmet pas cet attribut) ; le ralentissement et l'arrêt du sprint fonctionnent.
- L'équipement visible utilise des entités d'affichage : il peut ne pas apparaître sur console selon la version de Geyser.
- Les crafts avec durée concernent l'inventaire et l'établi vanilla (et les établis de mods basés dessus).
