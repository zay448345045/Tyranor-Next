(function () {
  "use strict";
  if (window.TyranorMod) return;

  var FLAG_DEFAULTS = {
    godMode: false,
    oneHit: false,
    alwaysCrit: false,
    noclip: false,
    eventSpeed: false,
    msgSkip: false
  };
  var state = Object.assign({}, FLAG_DEFAULTS);
  var hooksInstalled = false;
  var readyListeners = [];

  function bridge() { return window.TyranorModNative || null; }
  function clone(value) { return JSON.parse(JSON.stringify(value)); }
  function number(value, fallback) {
    var result = Number(value);
    return Number.isFinite(result) ? result : (fallback || 0);
  }
  function integer(value, fallback) { return Math.trunc(number(value, fallback)); }
  function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
  function promise(value) {
    return value && typeof value.then === "function" ? value : Promise.resolve(value);
  }
  function isReady() {
    return !!(window.DataManager && window.$gameParty && window.$dataSystem);
  }
  function inBattle() {
    return !!(window.$gameParty && $gameParty.inBattle && $gameParty.inBattle());
  }
  function persistState() {
    var nativeBridge = bridge();
    if (!nativeBridge || !nativeBridge.setState) return;
    try { nativeBridge.setState(JSON.stringify(state)); } catch (error) { console.warn("TyranorMod state save failed", error); }
  }
  function loadState() {
    var nativeBridge = bridge();
    if (!nativeBridge || !nativeBridge.getState) return;
    try {
      var saved = JSON.parse(nativeBridge.getState() || "{}");
      Object.keys(FLAG_DEFAULTS).forEach(function (key) {
        if (typeof saved[key] === "boolean") state[key] = saved[key];
      });
    } catch (error) { console.warn("TyranorMod state load failed", error); }
  }
  function original(target, name) {
    var fn = target && target[name];
    return fn && (fn.__tyranorModOriginal || fn);
  }
  function hook(target, name, factory) {
    if (!target || typeof target[name] !== "function") return false;
    if (target[name].__tyranorModHook) return true;
    var base = original(target, name);
    var wrapped = factory(base);
    wrapped.__tyranorModHook = true;
    wrapped.__tyranorModOriginal = base;
    target[name] = wrapped;
    return true;
  }
  function installHooks() {
    if (!window.Game_Battler || !window.Game_Action || !window.Game_Player) return false;
    hook(Game_Battler.prototype, "gainHp", function (base) {
      return function (value) {
        if (state.godMode && this.isActor && this.isActor() && value < 0) value = 0;
        if (state.oneHit && this.isEnemy && this.isEnemy() && value < 0) value = -Math.max(1, this.hp || 1);
        return base.call(this, value);
      };
    });
    hook(Game_Action.prototype, "makeDamageValue", function (base) {
      return function (target, critical) {
        var forceCritical = state.alwaysCrit && this.isForOpponent && this.isForOpponent();
        var value = base.call(this, target, forceCritical ? true : critical);
        if (state.godMode && target && target.isActor && target.isActor() && value > 0) return 0;
        if (state.oneHit && target && target.isEnemy && target.isEnemy() && value > 0) return Math.max(value, target.hp || 1);
        return value;
      };
    });
    hook(Game_Player.prototype, "canPass", function (base) {
      return function () { return state.noclip ? true : base.apply(this, arguments); };
    });
    hook(Game_Player.prototype, "isCollidedWithCharacters", function (base) {
      return function () { return state.noclip ? false : base.apply(this, arguments); };
    });
    if (window.Game_Interpreter) {
      hook(Game_Interpreter.prototype, "updateWaitMode", function (base) {
        return function () {
          if (state.eventSpeed) {
            this._waitCount = 0;
            this._waitMode = "";
            return false;
          }
          return base.apply(this, arguments);
        };
      });
    }
    if (window.Window_Message) {
      hook(Window_Message.prototype, "isTriggered", function (base) {
        return function () { return state.msgSkip ? true : base.apply(this, arguments); };
      });
      hook(Window_Message.prototype, "updateInput", function (base) {
        return function () {
          if (state.msgSkip && this.pause) {
            this.pause = false;
            if (this.terminateMessage) this.terminateMessage();
            return true;
          }
          return base.apply(this, arguments);
        };
      });
    }
    if (window.DataManager) {
      hook(DataManager, "extractSaveContents", function (base) {
        return function () {
          var result = base.apply(this, arguments);
          setTimeout(reapplyPersistent, 0);
          return result;
        };
      });
    }
    hooksInstalled = true;
    reapplyPersistent();
    return true;
  }
  function reapplyPersistent() {
    if (window.$gamePlayer) $gamePlayer._through = !!state.noclip;
  }
  function setFlag(name, enabled) {
    if (!Object.prototype.hasOwnProperty.call(FLAG_DEFAULTS, name)) throw new Error("未知开关：" + name);
    state[name] = !!enabled;
    reapplyPersistent();
    persistState();
    return state[name];
  }
  function toggleFlag(name) { return setFlag(name, !state[name]); }
  function getState() { return clone(state); }

  function getGold() { return isReady() ? $gameParty.gold() : 0; }
  function setGold(value) {
    if (!isReady()) throw new Error("游戏尚未进入可修改状态");
    var target = clamp(integer(value), 0, $gameParty.maxGold ? $gameParty.maxGold() : 99999999);
    $gameParty.gainGold(target - $gameParty.gold());
    return $gameParty.gold();
  }
  function databaseFor(kind) {
    if (kind === "item") return window.$dataItems || [];
    if (kind === "weapon") return window.$dataWeapons || [];
    if (kind === "armor") return window.$dataArmors || [];
    if (kind === "skill") return window.$dataSkills || [];
    if (kind === "state") return window.$dataStates || [];
    throw new Error("未知数据库类型：" + kind);
  }
  function listDatabase(kind, query, offset, limit) {
    query = String(query || "").toLowerCase();
    offset = Math.max(0, integer(offset));
    limit = clamp(integer(limit, 100), 1, 300);
    var values = databaseFor(kind).filter(function (entry) {
      return entry && (!query || String(entry.name || "").toLowerCase().indexOf(query) >= 0 || String(entry.description || "").toLowerCase().indexOf(query) >= 0);
    });
    return {
      total: values.length,
      values: values.slice(offset, offset + limit).map(function (entry) {
        return { id: entry.id, name: entry.name || ("#" + entry.id), description: entry.description || "", count: itemCount(kind, entry.id) };
      })
    };
  }
  function itemCount(kind, id) {
    var entry = databaseFor(kind)[integer(id)];
    return entry && window.$gameParty ? $gameParty.numItems(entry) : 0;
  }
  function setItemCount(kind, id, value) {
    var entry = databaseFor(kind)[integer(id)];
    if (!entry || !window.$gameParty) throw new Error("物品不存在");
    var max = $gameParty.maxItems ? $gameParty.maxItems(entry) : 99;
    var target = clamp(integer(value), 0, max);
    $gameParty.gainItem(entry, target - $gameParty.numItems(entry), false);
    return $gameParty.numItems(entry);
  }
  function clearItems(kind) {
    databaseFor(kind).forEach(function (entry) { if (entry) setItemCount(kind, entry.id, 0); });
  }

  function actorObject(id) { return window.$gameActors && $gameActors.actor(integer(id)); }
  function actorView(actor) {
    return {
      id: actor.actorId(), name: actor.name(), level: actor.level,
      hp: actor.hp, mhp: actor.mhp, mp: actor.mp, mmp: actor.mmp,
      atk: actor.atk, def: actor.def, mat: actor.mat, mdf: actor.mdf, agi: actor.agi, luk: actor.luk
    };
  }
  function listActors() {
    if (!window.$gameParty) return [];
    return $gameParty.members().map(actorView);
  }
  var PARAMS = { mhp: 0, mmp: 1, atk: 2, def: 3, mat: 4, mdf: 5, agi: 6, luk: 7 };
  function setActorStat(id, key, value) {
    var actor = actorObject(id);
    if (!actor) throw new Error("角色不存在");
    var target = integer(value);
    if (key === "level") actor.changeLevel(clamp(target, 1, actor.maxLevel ? actor.maxLevel() : 99), false);
    else if (key === "hp") actor.setHp(clamp(target, 0, actor.mhp));
    else if (key === "mp") actor.setMp(clamp(target, 0, actor.mmp));
    else if (Object.prototype.hasOwnProperty.call(PARAMS, key)) {
      var paramId = PARAMS[key];
      var current = actor.param(paramId);
      actor.addParam(paramId, target - current);
    } else throw new Error("未知角色属性：" + key);
    actor.refresh();
    return actorView(actor);
  }
  function recoverActor(id) { var actor = actorObject(id); if (!actor) throw new Error("角色不存在"); actor.recoverAll(); return actorView(actor); }
  function recoverParty() { if ($gameParty && $gameParty.members) $gameParty.members().forEach(function (actor) { actor.recoverAll(); }); return listActors(); }
  function actorSkills(id) { var actor = actorObject(id); return actor ? actor.skills().map(function (s) { return { id: s.id, name: s.name }; }) : []; }
  function learnSkill(id, skillId) { var actor = actorObject(id); if (!actor) throw new Error("角色不存在"); actor.learnSkill(integer(skillId)); return actorSkills(id); }
  function forgetSkill(id, skillId) { var actor = actorObject(id); if (!actor) throw new Error("角色不存在"); actor.forgetSkill(integer(skillId)); return actorSkills(id); }
  function actorStates(id) { var actor = actorObject(id); return actor ? actor.states().map(function (s) { return { id: s.id, name: s.name }; }) : []; }
  function addState(id, stateId) { var actor = actorObject(id); if (!actor) throw new Error("角色不存在"); actor.addState(integer(stateId)); return actorStates(id); }
  function removeState(id, stateId) { var actor = actorObject(id); if (!actor) throw new Error("角色不存在"); actor.removeState(integer(stateId)); return actorStates(id); }
  function actorEquips(id) {
    var actor = actorObject(id);
    return actor ? actor.equips().map(function (item, slot) { return { slot: slot, id: item ? item.id : 0, name: item ? item.name : "无", type: item && window.DataManager && DataManager.isWeapon(item) ? "weapon" : "armor" }; }) : [];
  }
  function listEquipCandidates(id, slot) {
    var actor = actorObject(id);
    if (!actor) return [];
    return $gameParty.equipItems().filter(function (item) { return actor.canEquip(item) && actor.equipSlots()[integer(slot)] === item.etypeId; }).map(function (item) {
      return { id: item.id, name: item.name, type: DataManager.isWeapon(item) ? "weapon" : "armor" };
    });
  }
  function changeEquip(id, slot, kind, itemId) {
    var actor = actorObject(id);
    if (!actor) throw new Error("角色不存在");
    var item = integer(itemId) === 0 ? null : databaseFor(kind)[integer(itemId)];
    actor.changeEquip(integer(slot), item);
    return actorEquips(id);
  }

  function namedSystemList(kind, query, offset, limit) {
    var names = kind === "switch" ? ($dataSystem.switches || []) : ($dataSystem.variables || []);
    query = String(query || "").toLowerCase();
    var values = [];
    names.forEach(function (name, id) {
      if (!id || (!name && !query)) return;
      if (query && String(name || "").toLowerCase().indexOf(query) < 0 && String(id).indexOf(query) < 0) return;
      values.push({ id: id, name: name || (kind === "switch" ? "开关 " : "变量 ") + id, value: kind === "switch" ? $gameSwitches.value(id) : $gameVariables.value(id) });
    });
    offset = Math.max(0, integer(offset)); limit = clamp(integer(limit, 100), 1, 300);
    return { total: values.length, values: values.slice(offset, offset + limit) };
  }
  function listSwitches(query, offset, limit) { return namedSystemList("switch", query, offset, limit); }
  function setSwitch(id, value) { $gameSwitches.setValue(integer(id), !!value); return $gameSwitches.value(integer(id)); }
  function listVariables(query, offset, limit) { return namedSystemList("variable", query, offset, limit); }
  function setVariable(id, value) { $gameVariables.setValue(integer(id), value); return $gameVariables.value(integer(id)); }

  function listMaps(query, offset, limit) {
    query = String(query || "").toLowerCase();
    var values = (window.$dataMapInfos || []).filter(function (entry) { return entry && (!query || String(entry.name || "").toLowerCase().indexOf(query) >= 0); });
    offset = Math.max(0, integer(offset)); limit = clamp(integer(limit, 100), 1, 300);
    return { total: values.length, values: values.slice(offset, offset + limit).map(function (entry) { return { id: entry.id, name: entry.name || ("地图 " + entry.id) }; }) };
  }
  function currentPosition() { return window.$gamePlayer ? { mapId: $gameMap.mapId(), x: $gamePlayer.x, y: $gamePlayer.y, direction: $gamePlayer.direction() } : null; }
  function teleport(mapId, x, y, direction, fade) {
    if (!window.$gamePlayer) throw new Error("当前不在地图场景");
    $gamePlayer.reserveTransfer(integer(mapId), Math.max(0, integer(x)), Math.max(0, integer(y)), integer(direction, 2), integer(fade, 0));
    if (window.SceneManager && SceneManager._scene && SceneManager._scene.constructor && String(SceneManager._scene.constructor.name).indexOf("Scene_Map") >= 0 && $gamePlayer.performTransfer) $gamePlayer.performTransfer();
    return true;
  }
  function battleSnapshot() {
    if (!inBattle()) return { inBattle: false, actors: [], enemies: [] };
    function battler(value, index) { return { index: index, name: value.name(), hp: value.hp, mhp: value.mhp, mp: value.mp, mmp: value.mmp, dead: value.isDead() }; }
    return { inBattle: true, actors: $gameParty.battleMembers().map(battler), enemies: $gameTroop.members().map(battler) };
  }
  function setEnemyHp(index, hp) { var enemy = $gameTroop.members()[integer(index)]; if (!enemy) throw new Error("敌人不存在"); enemy.setHp(clamp(integer(hp), 0, enemy.mhp)); enemy.refresh(); return battleSnapshot(); }
  function killAllEnemies() { if (!inBattle()) throw new Error("当前不在战斗中"); $gameTroop.members().forEach(function (enemy) { enemy.setHp(0); enemy.refresh(); }); return battleSnapshot(); }
  function forceVictory() { if (!inBattle() || !window.BattleManager) throw new Error("当前不在战斗中"); if (BattleManager.processVictory) BattleManager.processVictory(); return true; }
  function forceEscape() { if (!inBattle() || !window.BattleManager) throw new Error("当前不在战斗中"); if (BattleManager.processEscape) return BattleManager.processEscape(); if (BattleManager.abort) BattleManager.abort(); return true; }

  function maxSaveSlots() { return DataManager.maxSavefiles ? (typeof DataManager.maxSavefiles === "function" ? DataManager.maxSavefiles() : DataManager.maxSavefiles) : 20; }
  function globalInfo() { try { return DataManager.loadGlobalInfo ? (DataManager.loadGlobalInfo() || []) : []; } catch (_) { return []; } }
  function listSaveSlots() {
    return promise(globalInfo()).then(function (info) {
      var values = [];
      for (var id = 1; id <= maxSaveSlots(); id++) {
        var entry = info && info[id];
        values.push({ id: id, exists: !!entry, title: entry && (entry.title || entry.globalId) || "", playtime: entry && entry.playtime || "", timestamp: entry && entry.timestamp || 0 });
      }
      return values;
    });
  }
  function saveGame(id) { return promise(DataManager.saveGame(integer(id))); }
  function loadGame(id) {
    return promise(DataManager.loadGame(integer(id))).then(function (result) {
      if (result === false) throw new Error("读取存档失败");
      reapplyPersistent();
      if (window.SceneManager && window.Scene_Map) SceneManager.goto(Scene_Map);
      return result;
    });
  }
  function deleteSave(id) {
    var nativeBridge = window.saveDataManager;
    var saveId = integer(id);
    var key = window.StorageManager && StorageManager.webStorageKey ? StorageManager.webStorageKey(saveId) :
      (window.StorageManager && StorageManager.makeSavename ? StorageManager.makeSavename(saveId) : saveId);
    if (nativeBridge && nativeBridge.Remove) nativeBridge.Remove(String(key));
    return Promise.resolve(true);
  }

  function onReady(callback) { if (isReady()) callback(); else readyListeners.push(callback); }
  loadState();
  window.TyranorMod = {
    version: "1.0.0", isReady: isReady, onReady: onReady, installHooks: installHooks,
    getState: getState, setFlag: setFlag, toggleFlag: toggleFlag, reapplyPersistent: reapplyPersistent,
    getGold: getGold, setGold: setGold, listDatabase: listDatabase, itemCount: itemCount, setItemCount: setItemCount, clearItems: clearItems,
    listActors: listActors, setActorStat: setActorStat, recoverActor: recoverActor, recoverParty: recoverParty,
    actorSkills: actorSkills, learnSkill: learnSkill, forgetSkill: forgetSkill,
    actorStates: actorStates, addState: addState, removeState: removeState,
    actorEquips: actorEquips, listEquipCandidates: listEquipCandidates, changeEquip: changeEquip,
    listSwitches: listSwitches, setSwitch: setSwitch, listVariables: listVariables, setVariable: setVariable,
    listMaps: listMaps, currentPosition: currentPosition, teleport: teleport,
    battleSnapshot: battleSnapshot, setEnemyHp: setEnemyHp, killAllEnemies: killAllEnemies, forceVictory: forceVictory, forceEscape: forceEscape,
    listSaveSlots: listSaveSlots, saveGame: saveGame, loadGame: loadGame, deleteSave: deleteSave, getMaxSaveSlots: maxSaveSlots
  };

  var readinessTimer = setInterval(function () {
    if (!isReady()) return;
    installHooks();
    reapplyPersistent();
    var listeners = readyListeners.splice(0);
    listeners.forEach(function (listener) { try { listener(); } catch (error) { console.error(error); } });
    clearInterval(readinessTimer);
  }, 250);
  setInterval(function () { if (isReady()) { installHooks(); reapplyPersistent(); } }, 3000);
})();
