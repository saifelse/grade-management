<?
$username = $_SERVER["REMOTE_USER"];
$target = "staff/grades/data/$_SERVER[REMOTE_USER].js";
if(file_exists($target)){
    echo file_get_content($target);
}
?>