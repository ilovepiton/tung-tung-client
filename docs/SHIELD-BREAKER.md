# Auto Shield Breaker — 1.35.1

1. Open Arsenal → Auto Shield Breaker and enable it. Its toggle key is independently configurable.
2. Put a mace and a usable axe in your hotbar. Hold the mace.
3. Aim at an opponent currently blocking with a shield, within normal reach, and click your mouse use button once (right-click by default).
4. Keep aiming at that opponent. The helper selects the axe and waits for normal attack cooldown before one axe attack, restores your original mace slot, then waits for observed shield release and mace cooldown before one mace attack.

A held button never repeats the combo. A normal left-click does not start it and cancels a pending combo. Changing the selected hotbar slot, opening a screen, losing focus, detaching the camera, changing sessions, losing the target or an inventory conflict cancels it. The module never turns the camera or searches for another opponent. Listed friends are excluded. The axe is selected only from the hotbar; it does not rearrange inventory.

The mace slot is restored only while still owned by the helper. If you select a different slot, your choice is respected. The combo times out after 60 client ticks, and gives up if the shield stays raised for more than 20 ticks after the axe attack. There are no attack retries. Server shield-disable/attack acceptance is not guaranteed. A lowered shield is an observed state, not proof that an axe broke it.

The dedicated helper runs even when the separate Mace PvP module has handled the tick; that bot pauses combat during the manual combo. Combat Bot inventory ownership takes priority. Streamer Mode pauses the helper.

Validation: Java build and 143 JUnit tests passed, including five new combo/gating cases. Windows compiled and Mac universal app signatures verified. No live multiplayer verification of the new combo has been completed.
