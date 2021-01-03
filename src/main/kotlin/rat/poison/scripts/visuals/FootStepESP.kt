package rat.poison.scripts.visuals

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Align
import rat.poison.*
import rat.poison.game.*
import rat.poison.game.entity.*
import rat.poison.game.forEntities
import rat.poison.overlay.App
import rat.poison.utils.Vector
import rat.poison.utils.every
import rat.poison.utils.generalUtil.*
import kotlin.math.pow
import kotlin.math.sqrt

val footSteps = Array(256) { FootStep() }
data class FootStep(var x: Float = 0F, var y: Float = 0F, var z: Float = 0F,
                            var ttl: Int = curSettings.int["FOOTSTEP_TTL"],
                            var open: Boolean = true, var myTeam: Boolean = false,
                            var ent: Entity = 0L)
private var stepTimer = 0

fun footStepEsp() {
    constructSteps()

    runFootSteps()
}

fun inFootsteps(to: Entity): Boolean {
    val distance = curSettings.int["AUDIBLE_ESP_RANGE"]
    footSteps.forEach {
        if (it.ent == to) {
            return !it.open && it.ttl > 0 && Vector(it.x, it.y, it.z).distanceTo(me.position()) <= distance
        }
    }
    return false
}


fun runFootSteps() = App {
    if (!curSettings.bool["ENABLE_ESP"]) return@App

    if (!curSettings.bool["ENABLE_FOOTSTEPS"]) return@App

    for (i in footSteps.indices) {
        if (!footSteps[i].open) {
            val color = if (footSteps[i].myTeam) {
                curSettings.colorGDX["FOOTSTEP_TEAM_COLOR"]
            } else {
                curSettings.colorGDX["FOOTSTEP_ENEMY_COLOR"]
            }
            color.a = footSteps[i].ttl / curSettings.float["FOOTSTEP_TTL"]

            if ((footSteps[i].myTeam && !curSettings.bool["FOOTSTEP_TEAM"]) || (!footSteps[i].myTeam && !curSettings.bool["FOOTSTEP_ENEMY"])) {
                continue
            }

            if (curSettings.int["FOOTSTEP_TYPE"] == 1) {
                //As text
                val inVec = Vector(footSteps[i].x, footSteps[i].y, footSteps[i].z)
                val outVec = worldToScreen(inVec)
                if (outVec.w2s()) {
                    val sbText = StringBuilder("Step")

                    sb.begin()

                    textRenderer.color = color
                    textRenderer.draw(sb, sbText, outVec.x, outVec.y, 1F, Align.left, false)

                    sb.end()
                }
                inVec.release()
                outVec.release()
            } else {
                //As circle
                val oldMatrix = Matrix4(shapeRenderer.projectionMatrix.values)
                shapeRenderer.apply {
                    if (isDrawing) {
                        end()
                    }

                    val gameMatrix = w2sViewMatrix.toMatrix4()

                    begin()
                    this.color = color

                    //Circle at position
                    gameMatrix.translate(0F, 0F, footSteps[i].z.cToFloat())
                    projectionMatrix = gameMatrix
                    circle(footSteps[i].x, footSteps[i].y, (curSettings.float["FOOTSTEP_TTL"] - footSteps[i].ttl.toFloat()) + 10F)
                    gameMatrix.translate(0F, 0F, -footSteps[i].z.cToFloat())

                    end()
                }

                shapeRenderer.projectionMatrix = oldMatrix
            }
        }
    }
}

private fun constructSteps() = every(10) {
    stepTimer+= 1
    if (stepTimer >= curSettings.int["FOOTSTEP_UPDATE"]) {
        forEntities(EntityType.CCSPlayer) {
            val ent = it.entity
            if (ent == me || ent.dead() || ent.dormant()) return@forEntities

            val inMyTeam = ent.team() == me.team()

            val entVel = ent.velocity()
            val entMag = sqrt(entVel.x.pow(2F) + entVel.y.pow(2F) + entVel.z.pow(2F))
            entVel.release()
            
            if (entMag >= 150) {
                val entPos = ent.absPosition()

                val idx = emptySlot()
                if (idx != -1) {
                    footSteps[idx].apply {
                        x = entPos.x
                        y = entPos.y
                        z = entPos.z
                        ttl = curSettings.int["FOOTSTEP_TTL"]
                        open = false
                        myTeam = inMyTeam
                        this.ent = ent
                    }
                }
    
                entPos.release()
            }
        }

        for (i in footSteps.indices) {
            footSteps[i].ttl -= 10
            if (footSteps[i].ttl <= 0) { //Reset
                footSteps[i].apply {
                    x = 0F
                    y = 0F
                    z = 0F
                    ttl = curSettings.int["FOOTSTEP_TTL"]
                    open = true
                    this.ent = 0L
                }
            }
        }

        stepTimer = 0
    }
}

private fun emptySlot(): Int {
    var idx = -1

    for (i in footSteps.indices) {
        if (footSteps[i].open) {
            idx = i
            break
        }
    }

    return idx
}