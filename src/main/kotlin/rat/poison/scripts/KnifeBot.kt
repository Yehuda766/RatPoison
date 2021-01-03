@file:Suppress("DEPRECATION")

package rat.poison.scripts

import com.badlogic.gdx.math.Vector3
import org.jire.kna.set
import rat.poison.curSettings
import rat.poison.game.CSGO.clientDLL
import rat.poison.game.angle
import rat.poison.game.clientState
import rat.poison.game.entity.*
import rat.poison.game.me
import rat.poison.game.offsets.ClientOffsets.dwForceAttack
import rat.poison.game.offsets.ClientOffsets.dwForceAttack2
import rat.poison.overlay.App
import rat.poison.overlay.opened
import rat.poison.robot
import rat.poison.scripts.aim.findTarget
import rat.poison.scripts.aim.meCurWep
import rat.poison.scripts.aim.meCurWepEnt
import rat.poison.scripts.aim.meDead
import rat.poison.settings.AIM_KEY
import rat.poison.settings.DANGER_ZONE
import rat.poison.utils.Vector
import rat.poison.utils.every
import rat.poison.utils.keyPressed
import java.awt.event.MouseEvent

private const val SwingDistance = 96f
private const val StabDistance = 64f

internal fun autoKnife() = every(10, inGameCheck = true) {
    if (!opened || !App.haveTarget || DANGER_ZONE || meDead || !curSettings.bool["ENABLE_AUTO_KNIFE"] || !meCurWep.knife) return@every
    val currentAngle = clientState.angle()
    val position = me.position()
    val target = findTarget(position, currentAngle, false, 32F, -2)
    if (target <= 0 || keyPressed(AIM_KEY)) return@every

    val targetPos = target.absPosition()
    val mePos = me.absPosition()
    val dst = mePos.distanceTo(targetPos)

    if (dst > SwingDistance) return@every

    val canStab = dst <= StabDistance
    if (!isBehindMe(targetPos)) {
        val imBehind = canBackStab(targetPos, target.direction())
        val attackType: KnifeAttackType = if (canStab) {
            val health = target.health()
            val hasArmor = target.armor() > 0
            val swingDmg =
                    (if (meCurWepEnt.nextPrimaryAttack() + .4f < me.time()) KnifeAttackType.SWING else KnifeAttackType.SLASH).getDmg(imBehind, hasArmor)
            val slashDmg = KnifeAttackType.SLASH.getDmg(imBehind, hasArmor)
            val stabDmg = KnifeAttackType.STAB.getDmg(imBehind, hasArmor)

            when {
                // IF health lower than swing_dmg, do a swing
                health <= swingDmg -> KnifeAttackType.SLASH
                // IF health lower than stab_dmg, do a stab
                health <= stabDmg -> KnifeAttackType.STAB
                // IF health more than swing+swing+stab, do a stab
                health > (swingDmg + slashDmg + stabDmg) -> KnifeAttackType.STAB
                // ELSE swing (initiate swing+swing+stab)
                else -> KnifeAttackType.SLASH
            }
        } else {
            val velocity = me.velocity()
            val absPos = me.absPosition()
            try {
                if (imBehind
                    && Vector3.len2(velocity.x, velocity.y, velocity.z) > 0
                    && absPos.distanceTo(targetPos) > StabDistance
                ) {
                    //wait to get close enough to be able to back stab
                    KnifeAttackType.NONE
                } else {
                    KnifeAttackType.SLASH
                }
            } finally {
                velocity.release()
                absPos.release()
            }
        }
        attackType.attack()
    }
    
    targetPos.release()
    mePos.release()
}

private val delta = Vector3()

private fun isBehindMe(position: Vector): Boolean {
    me.absPosition().use {
        delta.set(it.x, it.y, it.z)
            .sub(position.x, position.y, position.z)
    }
    delta.nor()
    val dir = me.direction()
    try {
        return delta.dot(dir. x, dir.y, dir.z) > 0.475f
    } finally {
    	dir.release()
    }
}

private fun canBackStab(position: Vector, direction: Vector): Boolean {
    me.absPosition().use {
        delta.set(position.x, position.y, position.z)
            .sub(it.x, it.y, it.z)
    }
    delta.nor()
    return delta.dot(direction.x, direction.y, direction.z) > 0.475f
}

var TRIGGER_FORCE_VALUES = false

private enum class KnifeAttackType(private val frontNoArmorDmg: Float = 0f,
                                   private val frontArmorDmg: Float = 0f,
                                   private val behindNoArmorDmg: Float = 0f,
                                   private val behindArmorDmg: Float = 0f) {
    NONE,
    SWING(40f, 34f, 90f, 76f),
    SLASH(25f, 21f, 90f, 76f),
    STAB(65f, 55f, 180f, 153f);

    fun getDmg(behind: Boolean, hasArmor: Boolean): Float = if (behind) {
        if (hasArmor) behindArmorDmg else behindNoArmorDmg
    } else {
        if (hasArmor) frontArmorDmg else frontNoArmorDmg
    }

    fun attack() {
        when (this) {
            NONE -> return
            STAB -> rightClick()
            //swing or slash
            else -> leftClick()
        }
    }
}

fun leftClick() {
    if (TRIGGER_FORCE_VALUES) {
        clientDLL[dwForceAttack] = 6
    } else {
        robot.mousePress(MouseEvent.BUTTON1_MASK)
        robot.mouseRelease(MouseEvent.BUTTON1_MASK)
    }
}

fun rightClick() {
    if (TRIGGER_FORCE_VALUES) {
        clientDLL[dwForceAttack2] = 6
    } else {
        robot.mousePress(MouseEvent.BUTTON3_MASK)
        robot.mouseRelease(MouseEvent.BUTTON3_MASK)
    }
}