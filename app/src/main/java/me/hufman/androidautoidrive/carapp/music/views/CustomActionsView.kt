package me.hufman.androidautoidrive.carapp.music.views

import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.Utils
import me.hufman.androidautoidrive.carapp.RHMIListAdapter
import me.hufman.androidautoidrive.music.CustomAction
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.idriveconnectionkit.rhmi.*

class CustomActionsView(val state: RHMIState, val phoneResources: PhoneAppResources, val musicController: MusicController) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state.componentsList.size == 1 &&
					state.componentsList[0] is RHMIComponent.List
		}
	}

	val listComponent: RHMIComponent.List
	val actionList = ArrayList<CustomAction>()
	val listAdapter = object: RHMIListAdapter<CustomAction>(3, actionList) {
		override fun convertRow(index: Int, item: CustomAction): Array<Any> {
			if (item.icon != null) {
				val invert = Utils.isDark(item.icon)
				return arrayOf(phoneResources.getBitmap(item.icon, 48, 48, invert), "", item.name)
			} else {
				return arrayOf("", "", item.name)
			}
		}
	}

	init {
		listComponent = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
	}

	fun initWidgets(playbackView: PlaybackView) {
		state.getTextModel()?.asRaDataModel()?.value = L.MUSIC_CUSTOMACTIONS_TITLE
		listComponent.asList()?.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
			val action = actionList.getOrNull(index)
			if (action != null) {
				musicController.customAction(action)
			}
			// show the playback view, but don't add it to the stack
			state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first().triggerEvent(mapOf(0.toByte() to playbackView.state.id))
		}
		listComponent.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "57,0,*")
	}

	fun show() {
		actionList.clear()
		actionList.addAll(musicController.getCustomActions())

		listComponent.getModel()?.setValue(listAdapter, 0, listAdapter.height, listAdapter.height)
	}
}